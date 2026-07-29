package dev.social.auth.authentication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload.
 *
 * <p>Validation here is intentionally loose - presence and a sane length,
 * nothing more. Enforcing the password policy on the way <em>in</em> would tell
 * an attacker that "abc" cannot possibly be anyone's password, and would lock
 * out accounts created before a policy change. The policy belongs on
 * registration; login only asks "does this match?".
 */
@Schema(name = "LoginRequest", description = "Credentials to exchange for a JWT")
public record LoginRequest(

        @Schema(example = "jdoe", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String username,

        @Schema(example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 72, message = "must be at most 72 characters")
        String password
) {

    /** Keeps the password out of logs and stack traces. */
    @Override
    public String toString() {
        return "LoginRequest{username='%s', password=***}".formatted(username);
    }
}
