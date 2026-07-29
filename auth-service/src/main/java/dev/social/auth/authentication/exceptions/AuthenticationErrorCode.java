package dev.social.auth.authentication.exceptions;

import dev.social.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Login failures.
 *
 * <p>{@link #INVALID_CREDENTIALS} covers both "no such user" and "wrong
 * password" on purpose. Separating them would turn the login endpoint into a
 * username oracle: an attacker could enumerate valid accounts just by reading
 * which error came back. The distinction is recorded in {@code login_audit} for
 * operators, never returned to the caller.
 */
public enum AuthenticationErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account temporarily locked");

    private final HttpStatus status;
    private final String title;

    AuthenticationErrorCode(HttpStatus status, String title) {
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
