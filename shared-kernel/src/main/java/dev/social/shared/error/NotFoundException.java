package dev.social.shared.error;

/**
 * The requested resource does not exist. Maps to whatever 404 code the calling
 * service passes in, so the client sees {@code PROFILE_NOT_FOUND} rather than a
 * generic "not found".
 */
public class NotFoundException extends ApplicationException {

    public NotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    /**
     * Convenience for the common "looked it up by id and got nothing" case.
     *
     * @param resource human name of the thing, e.g. {@code "Profile"}
     * @param id       identifier that produced no result
     */
    public static NotFoundException of(ErrorCode errorCode, String resource, Object id) {
        return new NotFoundException(errorCode, "%s not found: %s".formatted(resource, id));
    }
}
