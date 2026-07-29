-- =============================================================================
--  SEED 04 - likedb
--  Run with:  psql -h localhost -U like_service -d likedb -f 04-seed-likedb.sql
-- =============================================================================
--  Cross-likes so the timeline does not start at zero everywhere.
--  post_id -> postdb.posts.id   (see 03-seed-postdb.sql)
--  user_id -> authdb.users.id   (see 01-seed-authdb.sql)
--
--  Nobody likes their own post here, which makes "the like button starts
--  unpressed for the post author" easy to verify by hand.
--
--  Expected totals:  post 01 -> 3, post 02 -> 2, post 03 -> 2,
--                    post 04 -> 1, post 05 -> 4
-- =============================================================================

INSERT INTO post_likes (post_id, user_id, username) VALUES
    -- post 01 (jdoe)    <- mgarcia, lchen, kcamilo
    ('22222222-2222-2222-2222-222222220101', '11111111-1111-1111-1111-111111110102', 'mgarcia'),
    ('22222222-2222-2222-2222-222222220101', '11111111-1111-1111-1111-111111110103', 'lchen'),
    ('22222222-2222-2222-2222-222222220101', '11111111-1111-1111-1111-111111110105', 'kcamilo'),

    -- post 02 (mgarcia) <- jdoe, arossi
    ('22222222-2222-2222-2222-222222220102', '11111111-1111-1111-1111-111111110101', 'jdoe'),
    ('22222222-2222-2222-2222-222222220102', '11111111-1111-1111-1111-111111110104', 'arossi'),

    -- post 03 (lchen)   <- jdoe, kcamilo
    ('22222222-2222-2222-2222-222222220103', '11111111-1111-1111-1111-111111110101', 'jdoe'),
    ('22222222-2222-2222-2222-222222220103', '11111111-1111-1111-1111-111111110105', 'kcamilo'),

    -- post 04 (arossi)  <- mgarcia
    ('22222222-2222-2222-2222-222222220104', '11111111-1111-1111-1111-111111110102', 'mgarcia'),

    -- post 05 (kcamilo) <- everyone else
    ('22222222-2222-2222-2222-222222220105', '11111111-1111-1111-1111-111111110101', 'jdoe'),
    ('22222222-2222-2222-2222-222222220105', '11111111-1111-1111-1111-111111110102', 'mgarcia'),
    ('22222222-2222-2222-2222-222222220105', '11111111-1111-1111-1111-111111110103', 'lchen'),
    ('22222222-2222-2222-2222-222222220105', '11111111-1111-1111-1111-111111110104', 'arossi')
ON CONFLICT (post_id, user_id) DO NOTHING;

-- Build the read model from the write model using PROCEDURE #7 instead of
-- hand-writing the counter rows. If the procedure is broken, the seed fails
-- here rather than silently producing wrong totals.
CALL sp_rebuild_like_counters(NULL);

-- Sanity check
SELECT post_id, like_count
  FROM post_like_counters
 ORDER BY like_count DESC, post_id;
