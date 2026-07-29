-- =============================================================================
--  00-create-databases.sql
-- =============================================================================
--  Runs ONCE, the first time the Postgres container starts, from
--  /docker-entrypoint-initdb.d. Implements the "database per service" pattern
--  inside a single Postgres 17 instance.
--
--  Each microservice gets:
--    * its own LOGIN role
--    * its own database, OWNED by that role
--
--  Owning the database is what lets Flyway create objects in `public`
--  (since Postgres 15 the PUBLIC pseudo-role no longer has CREATE there).
--
--  NOTE: the passwords below are DEVELOPMENT-ONLY and must match the
--  SPRING_DATASOURCE_PASSWORD values in docker-compose.yml. For any
--  non-local environment inject them through secrets, never through a
--  file committed to git.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Service roles
-- ----------------------------------------------------------------------------
CREATE ROLE auth_service    WITH LOGIN PASSWORD 'auth_dev_pwd';
CREATE ROLE profile_service WITH LOGIN PASSWORD 'profile_dev_pwd';
CREATE ROLE post_service    WITH LOGIN PASSWORD 'post_dev_pwd';
CREATE ROLE like_service    WITH LOGIN PASSWORD 'like_dev_pwd';

-- ----------------------------------------------------------------------------
-- One database per service
-- ----------------------------------------------------------------------------
CREATE DATABASE authdb    OWNER auth_service    ENCODING 'UTF8';
CREATE DATABASE profiledb OWNER profile_service ENCODING 'UTF8';
CREATE DATABASE postdb    OWNER post_service    ENCODING 'UTF8';
CREATE DATABASE likedb    OWNER like_service    ENCODING 'UTF8';

COMMENT ON DATABASE authdb    IS 'auth-service    :8081 - credentials, roles, login audit';
COMMENT ON DATABASE profiledb IS 'profile-service :8082 - user profiles';
COMMENT ON DATABASE postdb    IS 'post-service    :8083 - posts';
COMMENT ON DATABASE likedb    IS 'like-service    :8084 - likes and like counters';

-- ----------------------------------------------------------------------------
-- Lock down `public` on every database: only the owner may create objects.
-- ----------------------------------------------------------------------------
\connect authdb
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT  ALL ON SCHEMA public TO   auth_service;

\connect profiledb
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT  ALL ON SCHEMA public TO   profile_service;

\connect postdb
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT  ALL ON SCHEMA public TO   post_service;

\connect likedb
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT  ALL ON SCHEMA public TO   like_service;
