package dev.social.like.likes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Which post to like or unlike.
 *
 * <p>No "like or unlike" flag: the endpoint toggles. A client that had to say
 * which it wanted would first have to know the current state, and by the time
 * it acted that state could have changed - the classic lost update. Toggling
 * makes the request self-consistent and lets the database decide.
 */
@Schema(name = "ToggleLikeRequest", description = "The post to toggle a like on")
public record ToggleLikeRequest(

        @Schema(example = "22222222-2222-2222-2222-222222220101",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "must not be null")
        UUID postId
) {
}
