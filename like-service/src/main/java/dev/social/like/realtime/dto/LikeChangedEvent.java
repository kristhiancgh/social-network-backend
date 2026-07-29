package dev.social.like.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * What travels over the WebSocket every time a like is toggled.
 *
 * <p>It carries both the shared fact and the personal one:
 * <ul>
 *   <li>{@code likeCount} is true for everybody - every connected browser
 *       applies it to that post's counter, no questions asked.</li>
 *   <li>{@code liked} is true only for {@code actorId}. A client updates its own
 *       heart only when {@code actorId} matches the user it is logged in as;
 *       otherwise it would light up because <em>somebody else</em> liked the
 *       post.</li>
 * </ul>
 *
 * <p>Sending both in one message is what keeps a second round trip out of the
 * hot path: the browser that clicked and the browsers that did not are served
 * by the same broadcast.
 *
 * @param postId        the post whose total changed
 * @param likeCount     new total, authoritative for every client
 * @param liked         whether {@code actorId} now likes the post
 * @param actorId       who toggled it
 * @param actorUsername their username, so a client can show "kris liked this"
 * @param occurredAt    server time, for ordering if messages arrive out of order
 */
@Schema(name = "LikeChangedEvent", description = "Real-time notification of a like change")
public record LikeChangedEvent(

        @Schema(example = "22222222-2222-2222-2222-222222220101")
        UUID postId,

        @Schema(description = "New total, applies to everyone", example = "4")
        long likeCount,

        @Schema(description = "Whether the actor now likes it - only meaningful for the actor",
                example = "true")
        boolean liked,

        @Schema(example = "11111111-1111-1111-1111-111111110105")
        UUID actorId,

        @Schema(example = "kcamilo")
        String actorUsername,

        @Schema(example = "2026-07-27T10:15:30Z")
        Instant occurredAt
) {

    public static LikeChangedEvent of(UUID postId,
                                      long likeCount,
                                      boolean liked,
                                      UUID actorId,
                                      String actorUsername) {
        return new LikeChangedEvent(postId, likeCount, liked, actorId, actorUsername, Instant.now());
    }
}
