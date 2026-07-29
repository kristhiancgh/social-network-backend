package dev.social.post.posts.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A publication as the API returns it.
 *
 * <p>There is no {@code likeCount} here, and that is intentional. Likes belong
 * to like-service and live in a different database; embedding a count would
 * force post-service to call it on every timeline render and to fail whenever
 * it is unavailable. The client fetches counts separately from
 * {@code GET /api/likes/counts} and keeps them fresh over the WebSocket - which
 * is what makes the real-time requirement work at all, since a number baked
 * into this payload would be stale the moment it was serialised.
 */
@Schema(name = "PostResponse", description = "A publication")
public record PostResponse(

        @Schema(example = "22222222-2222-2222-2222-222222220101")
        UUID id,

        @Schema(description = "Author's account id",
                example = "11111111-1111-1111-1111-111111110101")
        UUID authorId,

        @Schema(example = "jdoe")
        String authorUsername,

        @Schema(description = "Author's public handle at the time of publishing",
                example = "johnny")
        String authorAlias,

        @Schema(example = "First post on the network.")
        String message,

        @Schema(description = "Stamped by the database on insert",
                example = "2026-07-27T10:15:30Z")
        Instant publishedAt
) {
}
