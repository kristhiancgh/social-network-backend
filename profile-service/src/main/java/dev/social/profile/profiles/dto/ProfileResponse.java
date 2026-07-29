package dev.social.profile.profiles.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A profile as the API returns it: exactly the four fields the brief lists -
 * first name, last name, birth date, alias - plus derived conveniences.
 *
 * <p>{@code fullName} and {@code age} are computed rather than stored. Sending
 * them saves every client from reimplementing the same string concatenation and
 * the same leap-year arithmetic, and there is still only one source of truth.
 */
@Schema(name = "ProfileResponse", description = "Public profile of a user")
public record ProfileResponse(

        @Schema(description = "Profile row id", example = "33333333-3333-3333-3333-333333330101")
        UUID id,

        @Schema(description = "Owning account; matches the JWT subject",
                example = "11111111-1111-1111-1111-111111110101")
        UUID userId,

        @Schema(example = "John")
        String firstName,

        @Schema(example = "Doe")
        String lastName,

        @Schema(description = "First and last name joined", example = "John Doe")
        String fullName,

        @Schema(example = "1992-03-14")
        LocalDate birthDate,

        @Schema(description = "Completed years, computed on read", example = "34")
        int age,

        @Schema(description = "Public handle, unique across the network", example = "johnny")
        String alias,

        @Schema(example = "Backend engineer. Coffee first, deploy later.")
        String bio,

        @Schema(example = "https://cdn.social.dev/avatars/johnny.png")
        String avatarUrl,

        @Schema(example = "2026-07-27T10:15:30Z")
        Instant createdAt
) {
}
