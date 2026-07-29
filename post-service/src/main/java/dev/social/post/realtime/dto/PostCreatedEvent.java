package dev.social.post.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Announced over the WebSocket when somebody publishes.
 *
 * <p>It carries the whole post, not just its id. A client receiving only an id
 * would have to fetch the post before it could render it - one extra round trip
 * per publication, multiplied by every connected browser. The payload is small
 * enough that sending it outright is cheaper for everyone.
 *
 * <p>{@code authorId} is what lets a client decide whether the post belongs on
 * its screen at all: the timeline shows other people's publications, so a
 * browser ignores the event when the author is the user reading it.
 */
@Schema(name = "PostCreatedEvent", description = "Real-time notification of a new publication")
public record PostCreatedEvent(

        @Schema(example = "22222222-2222-2222-2222-222222220101")
        UUID postId,

        @Schema(example = "11111111-1111-1111-1111-111111110102")
        UUID authorId,

        @Schema(example = "mgarcia")
        String authorUsername,

        @Schema(example = "mary_g")
        String authorAlias,

        @Schema(example = "Redesigned the timeline card today.")
        String message,

        @Schema(description = "Stamped by the database on insert",
                example = "2026-07-28T10:15:30Z")
        Instant publishedAt,

        @Schema(example = "2026-07-28T10:15:30Z")
        Instant occurredAt
) {

    public static PostCreatedEvent of(UUID postId,
                                      UUID authorId,
                                      String authorUsername,
                                      String authorAlias,
                                      String message,
                                      Instant publishedAt) {
        return new PostCreatedEvent(
                postId, authorId, authorUsername, authorAlias, message, publishedAt, Instant.now());
    }
}
