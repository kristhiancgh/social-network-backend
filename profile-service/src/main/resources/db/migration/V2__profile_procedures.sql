-- =============================================================================
--  profiledb / V2 - stored procedures (PL/pgSQL)
-- =============================================================================
--  PROCEDURE #3 : sp_upsert_profile
-- =============================================================================


-- -----------------------------------------------------------------------------
--  PROCEDURE #3 - sp_upsert_profile
--
--  Creates the profile the first time a user is seen and updates it afterwards,
--  in one round trip and one transaction. The alias-uniqueness check excludes
--  the caller's own row so re-saving an unchanged profile is not a conflict.
--
--  p_created reports which branch was taken, letting the controller answer
--  201 Created vs 200 OK correctly.
--
--  Raised conditions:
--    P0001 / 'ALIAS_ALREADY_EXISTS'
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_upsert_profile(
    IN    p_user_id    UUID,
    IN    p_first_name VARCHAR,
    IN    p_last_name  VARCHAR,
    IN    p_birth_date DATE,
    IN    p_alias      VARCHAR,
    IN    p_bio        VARCHAR,
    INOUT p_profile_id UUID    DEFAULT NULL,
    INOUT p_created    BOOLEAN DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_alias VARCHAR(50) := btrim(p_alias);
BEGIN
    IF EXISTS (SELECT 1
                 FROM profiles p
                WHERE lower(p.alias) = lower(v_alias)
                  AND p.user_id <> p_user_id) THEN
        RAISE EXCEPTION 'ALIAS_ALREADY_EXISTS'
            USING ERRCODE = 'P0001',
                  HINT    = 'This alias is already taken';
    END IF;

    SELECT p.id INTO p_profile_id
      FROM profiles p
     WHERE p.user_id = p_user_id
       FOR UPDATE;

    IF p_profile_id IS NULL THEN
        INSERT INTO profiles (user_id, first_name, last_name, birth_date, alias, bio)
             VALUES (p_user_id, btrim(p_first_name), btrim(p_last_name),
                     p_birth_date, v_alias, p_bio)
          RETURNING id INTO p_profile_id;
        p_created := TRUE;
    ELSE
        UPDATE profiles
           SET first_name = btrim(p_first_name),
               last_name  = btrim(p_last_name),
               birth_date = p_birth_date,
               alias      = v_alias,
               bio        = p_bio
         WHERE id = p_profile_id;
        p_created := FALSE;
    END IF;
END;
$$;

COMMENT ON PROCEDURE sp_upsert_profile(UUID, VARCHAR, VARCHAR, DATE, VARCHAR, VARCHAR, UUID, BOOLEAN)
    IS 'Inserts or updates the profile of a user. p_created = TRUE when a new row was inserted.';
