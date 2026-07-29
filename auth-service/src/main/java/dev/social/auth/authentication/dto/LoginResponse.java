package dev.social.auth.authentication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a successful login returns.
 *
 * <p>{@code expiresIn} and {@code expiresAt} say the same thing twice on
 * purpose: the frontend schedules its refresh timer off the relative value
 * (immune to a wrong clock on the user's machine) while a human reading the
 * response wants the absolute one.
 */
@Schema(name = "LoginResponse", description = "Issued access token plus the identity behind it")
public record LoginResponse(

        @Schema(description = "Signed JWT. Send as: Authorization: Bearer <token>",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTEx...")
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Lifetime in seconds", example = "7200")
        long expiresIn,

        @Schema(example = "2026-07-27T12:15:30Z")
        Instant expiresAt,

        @Schema(description = "Who the token belongs to")
        AuthenticatedUserSummary user
) {

    public static final String BEARER = "Bearer";

    /**
     * Just enough identity for the UI to render a header without a follow-up
     * call. Everything else - real name, birth date, alias - comes from
     * profile-service, which is where personal data belongs.
     */
    @Schema(name = "AuthenticatedUserSummary")
    public record AuthenticatedUserSummary(

            @Schema(example = "11111111-1111-1111-1111-111111110101")
            UUID id,

            @Schema(example = "jdoe")
            String username,

            @Schema(example = "[\"ROLE_USER\"]")
            List<String> roles
    ) {
    }
}
