-- =============================================================================
--  SEED 03 - postdb
--  Run with:  psql -h localhost -U post_service -d postdb -f 03-seed-postdb.sql
-- =============================================================================
--  One publication per seeded user, as required by the spec.
--  published_at is staggered backwards so the timeline (ORDER BY published_at
--  DESC) has a stable, meaningful order instead of five identical timestamps.
--
--  author_id  -> authdb.users.id      (see 01-seed-authdb.sql)
--  author_alias -> profiledb.profiles.alias (see 02-seed-profiledb.sql)
-- =============================================================================

INSERT INTO posts (id, author_id, author_username, author_alias, message, published_at) VALUES
    ('22222222-2222-2222-2222-222222220101',
     '11111111-1111-1111-1111-111111110101', 'jdoe', 'johnny',
     'First post on the network. The stored procedure that toggles likes is prettier than I expected.',
     now() - INTERVAL '5 hours'),

    ('22222222-2222-2222-2222-222222220102',
     '11111111-1111-1111-1111-111111110102', 'mgarcia', 'mary_g',
     'Redesigned the timeline card today. Fewer borders, more breathing room.',
     now() - INTERVAL '4 hours'),

    ('22222222-2222-2222-2222-222222220103',
     '11111111-1111-1111-1111-111111110103', 'lchen', 'li_chen',
     'Denormalising the author name into the posts table removed an entire service call per row.',
     now() - INTERVAL '3 hours'),

    ('22222222-2222-2222-2222-222222220104',
     '11111111-1111-1111-1111-111111110104', 'arossi', 'aisha_r',
     'Signals plus a SignalStore made the like counter update without a single manual subscription.',
     now() - INTERVAL '2 hours'),

    ('22222222-2222-2222-2222-222222220105',
     '11111111-1111-1111-1111-111111110105', 'kcamilo', 'kris',
     'Five services, four databases, one docker compose up. Open two browser tabs and watch the likes sync.',
     now() - INTERVAL '1 hour')
ON CONFLICT (id) DO NOTHING;

-- Sanity check: this is exactly the timeline query the API runs.
SELECT author_alias, published_at, left(message, 45) || '...' AS preview
  FROM posts
 WHERE deleted = FALSE
 ORDER BY published_at DESC;
