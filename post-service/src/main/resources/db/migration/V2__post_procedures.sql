-- =============================================================================
--  postdb / V2 - stored procedures (PL/pgSQL)
-- =============================================================================
--  PROCEDURE #4 : sp_create_post
--  PROCEDURE #5 : sp_soft_delete_post
-- =============================================================================


-- -----------------------------------------------------------------------------
--  PROCEDURE #4 - sp_create_post
--
--  Single entry point for publishing. It normalises the message, applies the
--  "published_at defaults to now()" rule from the spec, and enforces a simple
--  anti-flood rule (no two identical messages from the same author within
--  30 seconds) which cannot be done race-free from the application layer.
--
--  Returns the generated id and the effective publication timestamp so the API
--  can echo them back without a second SELECT.
--
--  Raised conditions:
--    P0001 / 'EMPTY_MESSAGE'
--    P0001 / 'DUPLICATE_POST'
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_create_post(
    IN    p_author_id       UUID,
    IN    p_author_username VARCHAR,
    IN    p_author_alias    VARCHAR,
    IN    p_message         VARCHAR,
    INOUT p_post_id         UUID        DEFAULT NULL,
    INOUT p_published_at    TIMESTAMPTZ DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_message VARCHAR(500) := btrim(p_message);
BEGIN
    IF v_message IS NULL OR length(v_message) = 0 THEN
        RAISE EXCEPTION 'EMPTY_MESSAGE'
            USING ERRCODE = 'P0001',
                  HINT    = 'The post message cannot be blank';
    END IF;

    IF EXISTS (SELECT 1
                 FROM posts p
                WHERE p.author_id = p_author_id
                  AND p.message   = v_message
                  AND p.deleted   = FALSE
                  AND p.published_at > now() - INTERVAL '30 seconds') THEN
        RAISE EXCEPTION 'DUPLICATE_POST'
            USING ERRCODE = 'P0001',
                  HINT    = 'You just published this exact message';
    END IF;

    INSERT INTO posts (author_id, author_username, author_alias, message, published_at)
         VALUES (p_author_id, p_author_username, p_author_alias, v_message, now())
      RETURNING id, published_at INTO p_post_id, p_published_at;
END;
$$;

COMMENT ON PROCEDURE sp_create_post(UUID, VARCHAR, VARCHAR, VARCHAR, UUID, TIMESTAMPTZ)
    IS 'Publishes a post with published_at = now() and blocks duplicate spam within 30s.';


-- -----------------------------------------------------------------------------
--  PROCEDURE #5 - sp_soft_delete_post
--
--  Marks a post as deleted only if the caller owns it. Ownership and mutation
--  happen under the same row lock, closing the check-then-act window.
--
--  Raised conditions:
--    P0001 / 'POST_NOT_FOUND'
--    P0001 / 'NOT_POST_OWNER'
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_soft_delete_post(
    IN    p_post_id   UUID,
    IN    p_author_id UUID,
    INOUT p_deleted   BOOLEAN DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_owner   UUID;
    v_already BOOLEAN;
BEGIN
    SELECT p.author_id, p.deleted
      INTO v_owner, v_already
      FROM posts p
     WHERE p.id = p_post_id
       FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'POST_NOT_FOUND'
            USING ERRCODE = 'P0001';
    END IF;

    IF v_owner <> p_author_id THEN
        RAISE EXCEPTION 'NOT_POST_OWNER'
            USING ERRCODE = 'P0001',
                  HINT    = 'Only the author can delete this post';
    END IF;

    IF v_already THEN
        p_deleted := FALSE;   -- already gone, nothing changed
        RETURN;
    END IF;

    UPDATE posts SET deleted = TRUE WHERE id = p_post_id;
    p_deleted := TRUE;
END;
$$;

COMMENT ON PROCEDURE sp_soft_delete_post(UUID, UUID, BOOLEAN)
    IS 'Soft-deletes a post after verifying ownership under a row lock.';
