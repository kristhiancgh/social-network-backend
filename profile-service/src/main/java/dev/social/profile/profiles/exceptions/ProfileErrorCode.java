package dev.social.profile.profiles.exceptions;

import dev.social.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Profile failures.
 *
 * <p>{@link #ALIAS_ALREADY_EXISTS} matches the string {@code sp_upsert_profile}
 * raises, so the database signal maps onto the API error by name.
 */
public enum ProfileErrorCode implements ErrorCode {

    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profile not found"),
    ALIAS_ALREADY_EXISTS(HttpStatus.CONFLICT, "Alias already taken"),
    PROFILE_SAVE_FAILED(HttpStatus.CONFLICT, "Profile could not be saved");

    private final HttpStatus status;
    private final String title;

    ProfileErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public static ProfileErrorCode fromProcedureCode(String procedureCode) {
        for (ProfileErrorCode candidate : values()) {
            if (candidate.name().equals(procedureCode)) {
                return candidate;
            }
        }
        return PROFILE_SAVE_FAILED;
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
