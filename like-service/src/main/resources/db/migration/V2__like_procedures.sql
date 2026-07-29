-- =============================================================================
--  likedb / V2 - stored procedures (PL/pgSQL)
-- =============================================================================
--  PROCEDURE #6 : sp_toggle_post_like       <-- called on every POST /api/likes
--  PROCEDURE #7 : sp_rebuild_like_counters
-- =============================================================================


-- -----------------------------------------------------------------------------
--  PROCEDURE #6 - sp_toggle_post_like
--
--  The core of the real-time feature. A single CALL performs the whole
--  like/unlike cycle atomically and hands back exactly the two values the
--  WebSocket broadcast needs: the caller's new state and the fresh total.
--
--  Why the counter row is locked first:
--    two users liking the same post at the same instant would otherwise both
--    read the old total and both write it back, losing one like. Taking
--    FOR UPDATE on post_like_counters serialises the pair on that single row,
--    while leaving likes on *other* posts fully parallel.
--
--  Doing this in the database rather than in Java means the invariant holds
--  even with several like-service replicas behind the gateway - there is no
--  distributed lock to get wrong.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_toggle_post_like(
    IN    p_post_id    UUID,
    IN    p_user_id    UUID,
    IN    p_username   VARCHAR,
    INOUT p_liked      BOOLEAN DEFAULT NULL,
    INOUT p_like_count BIGINT  DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_removed INTEGER;
BEGIN
    -- Make sure the counter row exists, then serialise on it.
    INSERT INTO post_like_counters (post_id, like_count)
         VALUES (p_post_id, 0)
    ON CONFLICT (post_id) DO NOTHING;

    PERFORM 1
       FROM post_like_counters c
      WHERE c.post_id = p_post_id
        FOR UPDATE;

    -- Toggle: if a like already existed it is removed, otherwise it is created.
    DELETE FROM post_likes
     WHERE post_id = p_post_id
       AND user_id = p_user_id;
    GET DIAGNOSTICS v_removed = ROW_COUNT;

    IF v_removed > 0 THEN
        p_liked := FALSE;
    ELSE
        INSERT INTO post_likes (post_id, user_id, username)
             VALUES (p_post_id, p_user_id, btrim(p_username));
        p_liked := TRUE;
    END IF;

    -- Recompute from the write model; cheap because idx_post_likes_post covers it.
    UPDATE post_like_counters c
       SET like_count = (SELECT count(*) FROM post_likes l WHERE l.post_id = p_post_id),
           updated_at = now()
     WHERE c.post_id = p_post_id
 RETURNING c.like_count INTO p_like_count;
END;
$$;

COMMENT ON PROCEDURE sp_toggle_post_like(UUID, UUID, VARCHAR, BOOLEAN, BIGINT)
    IS 'Atomically likes/unlikes a post and returns the new state plus the fresh total for the WebSocket broadcast.';


-- -----------------------------------------------------------------------------
--  PROCEDURE #7 - sp_rebuild_like_counters
--
--  Maintenance routine: rebuilds the whole read model from the write model.
--  Used after a bulk import or a seed run, and by the integration test that
--  proves the counters never drift from the underlying rows.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_rebuild_like_counters(
    INOUT p_rows_affected BIGINT DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO post_like_counters (post_id, like_count, updated_at)
    SELECT l.post_id, count(*), now()
      FROM post_likes l
     GROUP BY l.post_id
        ON CONFLICT (post_id)
        DO UPDATE SET like_count = EXCLUDED.like_count,
                      updated_at = now();

    -- Posts that lost every like keep a counter row, so zero it explicitly.
    UPDATE post_like_counters c
       SET like_count = 0,
           updated_at = now()
     WHERE NOT EXISTS (SELECT 1 FROM post_likes l WHERE l.post_id = c.post_id)
       AND c.like_count <> 0;

    SELECT count(*) INTO p_rows_affected FROM post_like_counters;
END;
$$;

COMMENT ON PROCEDURE sp_rebuild_like_counters(BIGINT)
    IS 'Rebuilds post_like_counters from post_likes. Returns the number of counter rows.';
