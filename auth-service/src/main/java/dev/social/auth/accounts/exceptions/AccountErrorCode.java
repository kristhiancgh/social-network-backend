package dev.social.auth.accounts.exceptions;

import dev.social.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Account-management failures.
 *
 * <p>The first two names match the strings {@code sp_register_user} raises, so
 * translating a database error into an API error is a lookup rather than a
 * hand-written mapping - see {@link #fromProcedureCode(String)}.
 */
public enum AccountErrorCode implements ErrorCode {

    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already taken"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already registered"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    INVALID_USERNAME_FORMAT(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid username"),
    REGISTRATION_FAILED(HttpStatus.CONFLICT, "Registration failed");

    private final HttpStatus status;
    private final String title;

    AccountErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    /**
     * Maps a code raised by a PL/pgSQL procedure onto its API counterpart.
     *
     * <p>Falls back to {@link #REGISTRATION_FAILED} instead of throwing: a
     * procedure gaining a new code should degrade to a sane 409, not crash the
     * request with a 500 about an unknown enum constant.
     */
    public static AccountErrorCode fromProcedureCode(String procedureCode) {
        for (AccountErrorCode candidate : values()) {
            if (candidate.name().equals(procedureCode)) {
                return candidate;
            }
        }
        return REGISTRATION_FAILED;
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
