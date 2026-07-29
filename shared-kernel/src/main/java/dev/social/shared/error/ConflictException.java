package dev.social.shared.error;

/**
 * The request cannot be applied to the current state of the resource:
 * a username already taken, an alias already claimed, a duplicate post.
 *
 * <p>Distinct from {@link BusinessRuleException} on purpose - a conflict is
 * about state that already exists, so retrying the identical request will keep
 * failing until something else changes.
 */
public class ConflictException extends ApplicationException {

    public ConflictException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public ConflictException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
