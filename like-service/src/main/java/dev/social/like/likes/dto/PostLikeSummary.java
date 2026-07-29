package dev.social.like.likes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Like state for one post, from the point of view of one user.
 *
 * <p>Returned in batches so a timeline showing twenty posts asks once, not
 * twenty times.
 */
@Schema(name = "PostLikeSummary", description = "Like total for a post, and whether you liked it")
public record PostLikeSummary(

        @Schema(example = "22222222-2222-2222-2222-222222220101")
        UUID postId,

        @Schema(description = "Total likes; 0 for a post nobody has liked", example = "3")
        long likeCount,

        @Schema(description = "Whether the calling user has liked it", example = "false")
        boolean likedByMe
) {
}
