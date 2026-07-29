package dev.social.shared.error;

import java.util.List;

/**
 * Base class for every failure this system raises on purpose.
 *
 * <p>The point of carrying an {@link ErrorCode} on the exception is that the
 * decision "which HTTP status does this deserve" is taken where the domain
 * knowledge lives - in the service that throws - and not in a giant
 * {@code if/else} inside the web layer. {@link GlobalExceptionHandler} then
 * only has to translate, never to decide.
 *
 * <p>Unchecked by design: these travel from a service method up through the
 * controller untouched, and forcing {@code throws} clauses through every layer
 * would buy nothing.
 */
public class ApplicationException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient List<FieldViolation> violations;

    public ApplicationException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, List.of(), null);
    }

    public ApplicationException(ErrorCode errorCode, String detail, Throwable cause) {
        this(errorCode, detail, List.of(), cause);
    }

    public ApplicationException(ErrorCode errorCode, String detail, List<FieldViolation> violations) {
        this(errorCode, detail, violations, null);
    }

    public ApplicationException(ErrorCode errorCode,
                                String detail,
                                List<FieldViolation> violations,
                                Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
