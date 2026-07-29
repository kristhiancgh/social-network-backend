-- =============================================================================
--  SEED 01 - authdb
--  Run with:  psql -h localhost -U auth_service -d authdb -f 01-seed-authdb.sql
-- =============================================================================
--  Idempotent: every statement uses ON CONFLICT DO NOTHING, so re-running the
--  script never fails and never duplicates rows.
--
--  Every seeded account shares the password:  Password123!
--  BCrypt hash (cost 10):
--    $2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C
--
--  The UUIDs below are FIXED ON PURPOSE. authdb, profiledb, postdb and likedb
--  cannot join across databases, so the seed data is stitched together by
--  agreeing on these literal ids:
--     user 1 ...0101   user 2 ...0102   user 3 ...0103
--     user 4 ...0104   user 5 ...0105
-- =============================================================================

INSERT INTO users (id, username, email, password_hash, enabled) VALUES
    ('11111111-1111-1111-1111-111111110101', 'jdoe',    'john.doe@social.dev',        '$2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C', TRUE),
    ('11111111-1111-1111-1111-111111110102', 'mgarcia', 'maria.garcia@social.dev',    '$2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C', TRUE),
    ('11111111-1111-1111-1111-111111110103', 'lchen',   'li.chen@social.dev',         '$2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C', TRUE),
    ('11111111-1111-1111-1111-111111110104', 'arossi',  'aisha.rossi@social.dev',     '$2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C', TRUE),
    ('11111111-1111-1111-1111-111111110105', 'kcamilo', 'kristhian.camilo@social.dev','$2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Everyone gets ROLE_USER; kcamilo is also ROLE_ADMIN.
INSERT INTO user_roles (user_id, role_id) VALUES
    ('11111111-1111-1111-1111-111111110101', 1),
    ('11111111-1111-1111-1111-111111110102', 1),
    ('11111111-1111-1111-1111-111111110103', 1),
    ('11111111-1111-1111-1111-111111110104', 1),
    ('11111111-1111-1111-1111-111111110105', 1),
    ('11111111-1111-1111-1111-111111110105', 2)
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Sanity check
SELECT u.username, r.name AS role
  FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
  JOIN roles r       ON r.id = ur.role_id
 ORDER BY u.username, r.name;
