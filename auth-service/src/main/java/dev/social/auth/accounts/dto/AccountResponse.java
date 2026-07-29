package dev.social.auth.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public view of an account. There is no {@code passwordHash} field and there
 * never will be - that is the whole reason this record exists instead of
 * serialising the entity.
 */
@Schema(name = "AccountResponse", description = "An account, without any secret material")
public record AccountResponse(

        @Schema(description = "Account id; the subject of every JWT this system issues",
                example = "11111111-1111-1111-1111-111111110101")
        UUID id,

        @Schema(example = "jdoe")
        String username,

        @Schema(example = "john.doe@social.dev")
        String email,

        @Schema(description = "Granted authorities", example = "[\"ROLE_USER\"]")
        List<String> roles,

        @Schema(example = "true")
        boolean enabled,

        @Schema(example = "2026-07-27T10:15:30Z")
        Instant createdAt
) {
}
