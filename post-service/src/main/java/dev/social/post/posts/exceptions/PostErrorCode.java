package dev.social.post.posts.exceptions;

import dev.social.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Post failures. The names match the codes raised by {@code sp_create_post} and
 * {@code sp_soft_delete_post}.
 */
public enum PostErrorCode implements ErrorCode {

    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Post not found"),

    NOT_POST_OWNER(HttpStatus.FORBIDDEN, "Not the author of this post"),

    EMPTY_MESSAGE(HttpStatus.UNPROCESSABLE_ENTITY, "Message must not be blank"),

    DUPLICATE_POST(HttpStatus.CONFLICT, "Duplicate post"),

    POST_CREATION_FAILED(HttpStatus.CONFLICT, "Post could not be created");

    private final HttpStatus status;
    private final String title;

    PostErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public static PostErrorCode fromProcedureCode(String procedureCode) {
        for (PostErrorCode candidate : values()) {
            if (candidate.name().equals(procedureCode)) {
                return candidate;
            }
        }
        return POST_CREATION_FAILED;
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
