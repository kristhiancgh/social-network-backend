package dev.social.shared.error;

/**
 * Names of the members this project adds on top of RFC 7807.
 *
 * <p>RFC 7807 fixes five members - {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance} - and explicitly allows extensions. The
 * constants below are those extensions, collected here so the handler, the
 * tests and the OpenAPI examples can never drift from one another by typo.
 */
public final class ErrorContract {

    /** Namespace for every {@code type} URI. Not dereferenced at runtime. */
    public static final String TYPE_BASE = "https://social.dev/errors/";

    /** Stable machine-readable code, e.g. {@code POST_NOT_FOUND}. */
    public static final String ERROR_CODE = "errorCode";

    /** Correlation id, echoed in the {@code X-Trace-Id} response header. */
    public static final String TRACE_ID = "traceId";

    /** Instant the error was produced, ISO-8601 UTC. */
    public static final String TIMESTAMP = "timestamp";

    /** Which microservice answered. Invaluable once a gateway is in front. */
    public static final String SERVICE = "service";

    /** Per-field failures; present only on validation errors. */
    public static final String ERRORS = "errors";

    private ErrorContract() {
        // constants only
    }
}
