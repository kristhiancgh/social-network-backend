package dev.social.shared.error;

import org.springframework.http.HttpStatus;

/**
 * A single entry in the error catalogue.
 *
 * <p>Implemented by one enum per service ({@code AuthErrorCode},
 * {@code PostErrorCode}, ...) plus the cross-cutting {@link CommonErrorCode}.
 * Keeping it an interface rather than one giant shared enum means a service can
 * add an error without recompiling the other three, while every service still
 * produces the exact same JSON shape.
 *
 * <p>The {@link #code()} value is the stable contract for API clients. Never
 * rename one: the Angular frontend and any integration test may branch on it.
 * HTTP status alone is not enough - three different business failures can all
 * be a 409.
 */
public interface ErrorCode {

    /**
     * Machine-readable identifier, {@code SCREAMING_SNAKE_CASE}.
     * Example: {@code POST_NOT_FOUND}.
     */
    String code();

    /** HTTP status this error maps to. */
    HttpStatus status();

    /**
     * Short human-readable summary. Goes into the {@code title} member of the
     * RFC 7807 payload and must not change per occurrence - the varying part
     * belongs in {@code detail}.
     */
    String title();

    /**
     * URI identifying the problem type, exposed as the {@code type} member.
     *
     * <p>Derived from {@link #code()} by default, so
     * {@code POST_NOT_FOUND} becomes
     * {@code https://social.dev/errors/post-not-found}.
     */
    default String type() {
        return ErrorContract.TYPE_BASE + code().toLowerCase().replace('_', '-');
    }
}
