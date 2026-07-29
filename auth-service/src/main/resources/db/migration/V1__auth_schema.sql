-- =============================================================================
--  authdb / V1 - schema
--  Owner: auth-service (:8081)
--  Applied automatically by Flyway on service startup.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Helper: keeps updated_at honest without the application having to remember.
-- ----------------------------------------------------------------------------
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
-- users : the credential store. Deliberately holds NO personal data
--         (names, birth date, alias) - that lives in profile-service.
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users               PRIMARY KEY (id),
    CONSTRAINT uk_users_username      UNIQUE (username),
    CONSTRAINT uk_users_email         UNIQUE (email),
    CONSTRAINT ck_users_username_fmt  CHECK (username ~ '^[a-z0-9_.]{3,50}$'),
    CONSTRAINT ck_users_email_fmt     CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

COMMENT ON TABLE  users               IS 'Authentication credentials. Personal data lives in profiledb.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash, cost 10. Never stores plain text.';

CREATE TRIGGER trg_users_touch_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- roles / user_roles : authorities embedded into the JWT claims
-- ----------------------------------------------------------------------------
CREATE TABLE roles (
    id   SMALLINT    NOT NULL,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id UUID     NOT NULL,
    role_id SMALLINT NOT NULL,
    CONSTRAINT pk_user_roles      PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

INSERT INTO roles (id, name) VALUES
    (1, 'ROLE_USER'),
    (2, 'ROLE_ADMIN');

-- ----------------------------------------------------------------------------
-- login_audit : every login attempt, successful or not.
--               Fed by sp_record_login_attempt (see V2).
-- ----------------------------------------------------------------------------
CREATE TABLE login_audit (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    -- Monotonic insertion order. This is what "consecutive failures since the
    -- last success" is computed from, NOT attempted_at: now() returns the
    -- transaction start time, so several attempts written inside one
    -- transaction all share a timestamp and a "> last success" comparison
    -- silently returns nothing. An identity column has no ties, ever.
    seq          BIGINT      NOT NULL GENERATED ALWAYS AS IDENTITY,
    username     VARCHAR(50) NOT NULL,
    successful   BOOLEAN     NOT NULL,
    failure_code VARCHAR(40),
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(255),
    -- clock_timestamp(), not now(): real wall-clock time per row, so the audit
    -- trail reflects when each attempt actually happened.
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_login_audit  PRIMARY KEY (id),
    CONSTRAINT uk_login_audit_seq UNIQUE (seq)
);

CREATE INDEX idx_login_audit_username_time
    ON login_audit (username, attempted_at DESC);

-- Covers the exact lookup sp_record_login_attempt performs.
CREATE INDEX idx_login_audit_username_seq
    ON login_audit (username, seq DESC);

CREATE INDEX idx_login_audit_failures
    ON login_audit (username, seq DESC)
    WHERE successful = FALSE;

COMMENT ON TABLE login_audit IS 'Append-only trail used for brute-force detection and reporting.';
