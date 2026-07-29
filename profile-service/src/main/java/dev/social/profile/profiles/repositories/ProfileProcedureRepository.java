package dev.social.profile.profiles.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.sql.Date;
import java.util.UUID;

/**
 * Calls {@code sp_upsert_profile}.
 *
 * <p>Same approach as auth-service: PostgreSQL 14+ returns the INOUT parameters
 * of a {@code CALL} as a one-row result set, so the procedure is run as an
 * ordinary query. That sidesteps the PostgreSQL JDBC driver's inability to hand
 * a {@code uuid} back through {@code CallableStatement.getObject(index, Class)},
 * which is where the JPA {@code StoredProcedureQuery} route dead-ends.
 */
@Repository
public class ProfileProcedureRepository {

    private static final String CALL_UPSERT_PROFILE =
            "CALL sp_upsert_profile(?, ?, ?, ?, ?, ?, NULL, NULL)";

    private final JdbcTemplate jdbcTemplate;

    public ProfileProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts the profile the first time and updates it afterwards, deciding
     * which in a single statement.
     *
     * <p>Doing the choice in the database rather than as
     * {@code if (exists) update else insert} in Java closes the window where two
     * concurrent first-time saves both see "no profile yet" and both insert,
     * with one dying on {@code uk_profiles_user_id}.
     *
     * @return whether a new row was inserted, which is what lets the controller
     *         answer 201 rather than 200
     */
    public UpsertResult upsertProfile(UUID userId,
                                      String firstName,
                                      String lastName,
                                      LocalDate birthDate,
                                      String alias,
                                      String bio) {

        return jdbcTemplate.queryForObject(
                CALL_UPSERT_PROFILE,
                (resultSet, rowNumber) -> new UpsertResult(
                        resultSet.getObject("p_profile_id", UUID.class),
                        resultSet.getBoolean("p_created")),
                userId, firstName, lastName, Date.valueOf(birthDate), alias, bio);
    }

    /**
     * @param profileId id of the row that was written
     * @param created   {@code true} when it was an insert, {@code false} on update
     */
    public record UpsertResult(UUID profileId, boolean created) {
    }
}
