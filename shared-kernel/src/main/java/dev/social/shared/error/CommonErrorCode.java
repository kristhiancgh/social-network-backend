package dev.social.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Errors that are not specific to any one domain: malformed requests, failed
 * validation, missing authentication, and the catch-all internal failure.
 *
 * <p>Domain failures do not belong here. {@code POST_NOT_FOUND} lives in
 * post-service's own enum; only things every service can produce are listed.
 */
public enum CommonErrorCode implements ErrorCode {

    /** Bean Validation rejected the payload. See the {@code errors} member. */
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"),

    /** Body was not parseable at all - broken JSON, wrong content type. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),

    /** A path variable or query parameter could not be converted. */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),

    /** A required query parameter or header was absent. */
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Missing parameter"),

    /** No credentials, or credentials that could not be verified. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),

    /** Token present but not usable: bad signature, wrong issuer, malformed. */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),

    /** Token was valid and has simply run out. Split from INVALID_TOKEN so the
     *  frontend can silently refresh instead of bouncing the user to login. */
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "Expired token"),

    /** Authenticated, but not allowed to do this. */
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),

    /** Nothing at that identifier. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    /** Right URL, wrong HTTP verb. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),

    /** The request conflicts with the current state of the resource. */
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),

    /** A database constraint fired that the service did not anticipate. */
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Data integrity violation"),

    /** Payload above the configured limit. */
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),

    /** Anything unplanned. The cause is logged, never returned to the client. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    /** A downstream dependency is unreachable. */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

    private final HttpStatus status;
    private final String title;

    CommonErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
