package dev.social.post.posts.dto;

import dev.social.post.posts.domain.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for publishing.
 *
 * <p>Only the message. The author comes from the JWT and the publication date
 * is stamped by the database - the brief says it defaults on save, and letting a
 * client choose it would allow backdating a post to the top of someone else's
 * timeline.
 */
@Schema(name = "CreatePostRequest", description = "A new publication")
public record CreatePostRequest(

        @Schema(description = "What to publish",
                example = "Just shipped the like counter. It updates in real time.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = Post.MAX_MESSAGE_LENGTH, message = "must be at most 500 characters")
        String message
) {
}
