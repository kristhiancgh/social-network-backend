package dev.social.auth.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up payload.
 *
 * <p>Only credentials. The personal data the spec asks for - names, birth date,
 * alias - is sent separately to {@code PUT /api/profiles/me}, because
 * profile-service owns it and auth-service must not hold a copy.
 */
@Schema(name = "RegisterRequest", description = "Credentials for a new account")
public record RegisterRequest(

        @Schema(description = "Unique handle, lowercase letters, digits, dot or underscore",
                example = "jdoe", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(min = 3, max = 50, message = "must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.]+$",
                message = "may only contain letters, digits, dot and underscore")
        String username,

        @Schema(description = "Contact email, unique across the network",
                example = "john.doe@social.dev", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        @Size(max = 150, message = "must be at most 150 characters")
        String email,

        /*
         * Length is the requirement that actually buys security; a maximum is
         * set as well because BCrypt silently ignores anything past 72 bytes,
         * so accepting a 200-character password would be a false promise.
         */
        @Schema(description = "At least 8 characters, with one letter and one digit",
                example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "must contain at least one letter and one digit")
        String password
) {
}
