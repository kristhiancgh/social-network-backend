package dev.social.shared.error;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers the business code a PL/pgSQL procedure raised.
 *
 * <p>The stored procedures signal domain failures with
 * {@code RAISE EXCEPTION 'USERNAME_ALREADY_EXISTS' USING ERRCODE = 'P0001'}.
 * By the time that reaches the service layer it has been wrapped several times
 * over - {@code JpaSystemException} around {@code PersistenceException} around
 * {@code GenericJDBCException} around the driver's {@code SQLException} - and
 * the useful part is buried in a message that reads
 * {@code "ERROR: USERNAME_ALREADY_EXISTS\n  Hint: ..."}.
 *
 * <p>This walks the cause chain, keeps only exceptions whose SQLState is
 * {@code P0001} (the state Postgres reserves for a user-raised exception, so
 * genuine driver or constraint errors are never mistaken for business ones) and
 * pulls the leading identifier back out.
 *
 * <p>Deliberately written against {@link SQLException} rather than the driver's
 * {@code PSQLException}: shared-kernel has no compile-time dependency on the
 * PostgreSQL driver, and should not gain one.
 */
public final class SqlErrorSupport {

    /** SQLSTATE that {@code RAISE EXCEPTION} uses unless told otherwise. */
    public static final String RAISED_EXCEPTION_STATE = "P0001";

    private static final Pattern SEVERITY_PREFIX =
            Pattern.compile("^\\s*(?:ERROR|FATAL|PANIC)\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUSINESS_CODE =
            Pattern.compile("^([A-Z][A-Z0-9_]{2,60})");

    private SqlErrorSupport() {
        // utility
    }

    /**
     * @return the raised code, e.g. {@code "USERNAME_ALREADY_EXISTS"}, or empty
     *         when this failure did not come from one of our procedures
     */
    public static Optional<String> raisedBusinessCode(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {

            if (current instanceof SQLException sqlException
                    && RAISED_EXCEPTION_STATE.equals(sqlException.getSQLState())) {

                Optional<String> code = extractCode(sqlException.getMessage());
                if (code.isPresent()) {
                    return code;
                }
            }

            // Self-referencing cause would otherwise spin forever.
            if (current.getCause() == current) {
                break;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> extractCode(String rawMessage) {
        if (rawMessage == null) {
            return Optional.empty();
        }
        String withoutSeverity = SEVERITY_PREFIX.matcher(rawMessage).replaceFirst("").trim();
        Matcher matcher = BUSINESS_CODE.matcher(withoutSeverity);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * Name of the database constraint that failed, when the failure was a
     * constraint violation rather than a raised exception. Lets a service turn
     * {@code uk_profiles_alias} into a meaningful message instead of leaking
     * the raw SQL error.
     */
    public static Optional<String> violatedConstraint(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {

            if (current instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                // 23xxx is the SQL standard class for integrity constraint violations.
                if (state != null && state.startsWith("23")) {
                    Matcher matcher = Pattern
                            .compile("constraint \"([^\"]+)\"")
                            .matcher(String.valueOf(sqlException.getMessage()));
                    if (matcher.find()) {
                        return Optional.of(matcher.group(1));
                    }
                }
            }

            if (current.getCause() == current) {
                break;
            }
        }
        return Optional.empty();
    }
}
