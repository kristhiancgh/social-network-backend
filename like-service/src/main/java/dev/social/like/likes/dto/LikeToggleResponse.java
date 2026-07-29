package dev.social.like.likes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * The answer to a toggle, for the client that made it.
 *
 * <p>The same information also goes out over the WebSocket to everyone else. It
 * is returned here too so the browser that clicked does not have to wait for its
 * own broadcast to come back before updating - the button responds immediately,
 * and the broadcast merely confirms what it already showed.
 */
@Schema(name = "LikeToggleResponse", description = "State after toggling a like")
public record LikeToggleResponse(

        @Schema(example = "22222222-2222-2222-2222-222222220101")
        UUID postId,

        @Schema(description = "Whether you now like this post", example = "true")
        boolean liked,

        @Schema(description = "The post's new total", example = "4")
        long likeCount
) {
}
