package dev.social.auth.accounts.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Calls the PL/pgSQL procedures that belong to authdb.
 *
 * <h2>Why JdbcTemplate and not JPA's StoredProcedureQuery</h2>
 * The first implementation used
 * {@code entityManager.createStoredProcedureQuery(...)} with
 * {@link jakarta.persistence.ParameterMode#INOUT} parameters. It reaches
 * PostgreSQL correctly but dies on the way back with
 * {@code "Unable to extract OUT/INOUT parameter value [Unsupported type
 * conversion]"}: the PostgreSQL JDBC driver's {@code CallableStatement} cannot
 * hand a {@code uuid} OUT parameter back as a {@code java.util.UUID}.
 *
 * <p>PostgreSQL 14+ makes that whole layer unnecessary. A {@code CALL} against a
 * procedure with INOUT parameters <em>returns a one-row result set</em> holding
 * their final values, so it can be run as an ordinary query and read column by
 * column - no OUT registration, no driver escape-syntax translation, no
 * {@code escapeSyntaxCallMode} setting to get wrong.
 *
 * <p>Hibernate is still the ORM: every entity, every query and every
 * transaction goes through JPA. This class is the deliberate exception, and it
 * is a thin one. It shares the transaction with JPA because Spring hands
 * {@link JdbcTemplate} the same connection through {@code DataSourceUtils} -
 * but it does <em>not</em> flush the persistence context first, so never call a
 * procedure that depends on entity changes made earlier in the same
 * transaction.
 *
 * <p>The {@code NULL} in each statement is the INOUT parameter's inbound value.
 * The procedure overwrites it and returns it in the result row.
 */
@Repository
public class AccountProcedureRepository {

    private static final String CALL_REGISTER_USER =
            "CALL sp_register_user(?, ?, ?, NULL)";

    private static final String CALL_RECORD_LOGIN_ATTEMPT =
            "CALL sp_record_login_attempt(?, ?, ?, ?, ?, ?, NULL)";

    private final JdbcTemplate jdbcTemplate;

    public AccountProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs {@code sp_register_user}: inserts the user and grants ROLE_USER in a
     * single transaction, so an account can never exist without an authority.
     *
     * <p>Uniqueness is checked inside the procedure, which raises
     * {@code USERNAME_ALREADY_EXISTS} / {@code EMAIL_ALREADY_EXISTS} with
     * SQLSTATE {@code P0001}. The service translates them through
     * {@code dev.social.shared.error.SqlErrorSupport}.
     *
     * @return id of the newly created user
     */
    public UUID registerUser(String username, String email, String passwordHash) {
        return jdbcTemplate.queryForObject(
                CALL_REGISTER_USER,
                (resultSet, rowNumber) -> resultSet.getObject("p_user_id", UUID.class),
                username, email, passwordHash);
    }

    /**
     * Runs {@code sp_record_login_attempt}: writes the audit row and reports how
     * many failures have piled up since the last success.
     *
     * <p>Counting inside the procedure keeps the insert and the count in one
     * transaction; doing it as a follow-up query would let two simultaneous
     * failed logins both read the pre-insert total and under-report.
     *
     * @param windowMinutes how far back failures still count; older ones are
     *                      ignored, which is what makes a lockout expire on its own
     * @return consecutive failures since the last successful login and within
     *         the window, or {@code 0} when this attempt succeeded
     */
    public int recordLoginAttempt(String username,
                                  boolean successful,
                                  String failureCode,
                                  String ipAddress,
                                  String userAgent,
                                  int windowMinutes) {

        Integer consecutiveFailures = jdbcTemplate.queryForObject(
                CALL_RECORD_LOGIN_ATTEMPT,
                (resultSet, rowNumber) -> resultSet.getObject("p_consecutive_fails", Integer.class),
                username, successful, failureCode, ipAddress, userAgent, windowMinutes);

        return consecutiveFailures == null ? 0 : consecutiveFailures;
    }
}
