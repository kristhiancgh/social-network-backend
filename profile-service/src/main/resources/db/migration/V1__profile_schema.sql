-- =============================================================================
--  profiledb / V1 - schema
--  Owner: profile-service (:8082)
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- ----------------------------------------------------------------------------
-- profiles : the personal data required by the spec
--            (first name, last name, birth date, alias).
--
--  user_id is a LOGICAL foreign key to authdb.users.id. It is intentionally
--  NOT a real FK: crossing database boundaries would couple the two services
--  at the storage layer and break the "database per service" rule. Referential
--  integrity is owned by the application (a profile is only ever created for a
--  subject taken from a verified JWT).
-- ----------------------------------------------------------------------------
CREATE TABLE profiles (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name  VARCHAR(80) NOT NULL,
    birth_date DATE        NOT NULL,
    alias      VARCHAR(50) NOT NULL,
    bio        VARCHAR(280),
    avatar_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_profiles              PRIMARY KEY (id),
    CONSTRAINT uk_profiles_user_id      UNIQUE (user_id),
    CONSTRAINT uk_profiles_alias        UNIQUE (alias),
    CONSTRAINT ck_profiles_first_name   CHECK (length(btrim(first_name)) > 0),
    CONSTRAINT ck_profiles_last_name    CHECK (length(btrim(last_name)) > 0),
    CONSTRAINT ck_profiles_alias_fmt    CHECK (alias ~ '^[a-zA-Z0-9_.]{3,50}$'),
    CONSTRAINT ck_profiles_birth_past   CHECK (birth_date < CURRENT_DATE),
    CONSTRAINT ck_profiles_birth_sane   CHECK (birth_date > DATE '1900-01-01')
);

COMMENT ON TABLE  profiles         IS 'Public profile of a user. One row per authenticated subject.';
COMMENT ON COLUMN profiles.user_id IS 'Logical FK to authdb.users.id - not enforced across databases by design.';
COMMENT ON COLUMN profiles.alias   IS 'Public display handle, unique across the network.';

CREATE INDEX idx_profiles_alias_lower ON profiles (lower(alias));

CREATE TRIGGER trg_profiles_touch_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
