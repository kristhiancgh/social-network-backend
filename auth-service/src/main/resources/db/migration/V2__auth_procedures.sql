-- =============================================================================
--  authdb / V2 - stored procedures (PL/pgSQL)
-- =============================================================================
--  PROCEDURE #1 : sp_register_user
--  PROCEDURE #2 : sp_record_login_attempt
--
--  Both are true PROCEDUREs (CALL ...), not FUNCTIONs. Values travel back to
--  the caller through INOUT parameters, which is how Postgres procedures
--  return data and what Hibernate's StoredProcedureQuery expects.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  PROCEDURE #1 - sp_register_user
--
--  Creates a user and grants ROLE_USER in a single atomic unit, so a user can
--  never end up persisted without an authority. Uniqueness is checked
--  explicitly to return a stable business error code instead of leaking the
--  raw constraint name to the API layer.
--
--  Raised conditions:
--    P0001 / 'USERNAME_ALREADY_EXISTS'
--    P0001 / 'EMAIL_ALREADY_EXISTS'
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_register_user(
    IN    p_username      VARCHAR,
    IN    p_email         VARCHAR,
    IN    p_password_hash VARCHAR,
    INOUT p_user_id       UUID DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_username VARCHAR(50)  := lower(btrim(p_username));
    v_email    VARCHAR(150) := lower(btrim(p_email));
BEGIN
    IF EXISTS (SELECT 1 FROM users u WHERE u.username = v_username) THEN
        RAISE EXCEPTION 'USERNAME_ALREADY_EXISTS'
            USING ERRCODE = 'P0001',
                  HINT    = 'Choose a different username';
    END IF;

    IF EXISTS (SELECT 1 FROM users u WHERE u.email = v_email) THEN
        RAISE EXCEPTION 'EMAIL_ALREADY_EXISTS'
            USING ERRCODE = 'P0001',
                  HINT    = 'This email is already registered';
    END IF;

    INSERT INTO users (username, email, password_hash)
         VALUES (v_username, v_email, p_password_hash)
      RETURNING id INTO p_user_id;

    INSERT INTO user_roles (user_id, role_id)
         VALUES (p_user_id, 1);   -- ROLE_USER
END;
$$;

COMMENT ON PROCEDURE sp_register_user(VARCHAR, VARCHAR, VARCHAR, UUID)
    IS 'Atomically registers a user and assigns ROLE_USER. Returns the new id via p_user_id.';


-- -----------------------------------------------------------------------------
--  PROCEDURE #2 - sp_record_login_attempt
--
--  Writes the audit row AND reports how many consecutive failures the account
--  has accumulated since its last successful login, so the service can decide
--  whether to lock it. Doing the count here keeps it in the same transaction
--  as the insert - no read-after-write race.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_record_login_attempt(
    IN    p_username           VARCHAR,
    IN    p_successful         BOOLEAN,
    IN    p_failure_code       VARCHAR,
    IN    p_ip_address         VARCHAR,
    IN    p_user_agent         VARCHAR,
    -- Failures older than this stop counting, so a lockout built on this value
    -- expires by itself. Without a window, five typos would lock an account
    -- forever: the only thing that clears the streak is a successful login, and
    -- a locked account cannot produce one.
    IN    p_window_minutes     INTEGER DEFAULT 15,
    INOUT p_consecutive_fails  INTEGER DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_username         VARCHAR(50) := lower(btrim(p_username));
    v_last_success_seq BIGINT;
BEGIN
    INSERT INTO login_audit (username, successful, failure_code, ip_address, user_agent)
         VALUES (v_username,
                 p_successful,
                 CASE WHEN p_successful THEN NULL ELSE p_failure_code END,
                 left(p_ip_address, 45),
                 left(p_user_agent, 255));

    IF p_successful THEN
        p_consecutive_fails := 0;
        RETURN;
    END IF;

    -- Ordering is taken from the identity column, never from attempted_at.
    -- Inside a single transaction now() is frozen at transaction start, so
    -- timestamp comparison would find zero failures "after" a success written
    -- moments earlier - which is exactly the bug an integration test caught.
    SELECT max(la.seq)
      INTO v_last_success_seq
      FROM login_audit la
     WHERE la.username = v_username
       AND la.successful = TRUE;

    SELECT count(*)
      INTO p_consecutive_fails
      FROM login_audit la
     WHERE la.username = v_username
       AND la.successful = FALSE
       AND (v_last_success_seq IS NULL OR la.seq > v_last_success_seq)
       AND la.attempted_at > clock_timestamp() - make_interval(mins => p_window_minutes);
END;
$$;

COMMENT ON PROCEDURE sp_record_login_attempt(VARCHAR, BOOLEAN, VARCHAR, VARCHAR, VARCHAR, INTEGER, INTEGER)
    IS 'Audits a login attempt and returns consecutive failures since the last success, within a rolling window.';
