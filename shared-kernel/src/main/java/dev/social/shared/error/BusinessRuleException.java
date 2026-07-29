package dev.social.shared.error;

/**
 * A domain rule rejected an otherwise well-formed request: publishing a blank
 * message, deleting a post you do not own, logging in with bad credentials.
 *
 * <p>The HTTP status comes from the {@link ErrorCode}, so the same exception
 * type can surface as 400, 403 or 422 depending on which rule fired.
 */
public class BusinessRuleException extends ApplicationException {

    public BusinessRuleException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public BusinessRuleException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
