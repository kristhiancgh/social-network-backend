package dev.social.profile.profiles.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Payload for creating or updating your own profile.
 *
 * <p>There is no {@code userId} field, deliberately. The owner is taken from
 * the verified JWT, so a caller cannot write to somebody else's profile by
 * changing a number in the body - the commonest way this kind of endpoint is
 * abused.
 */
@Schema(name = "UpsertProfileRequest", description = "Personal details for the authenticated user")
public record UpsertProfileRequest(

        @Schema(example = "John", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 80, message = "must be at most 80 characters")
        String firstName,

        @Schema(example = "Doe", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 80, message = "must be at most 80 characters")
        String lastName,

        @Schema(example = "1992-03-14", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "must not be null")
        @Past(message = "must be a date in the past")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDate,

        @Schema(description = "Public handle, unique across the network",
                example = "johnny", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(min = 3, max = 50, message = "must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.]+$",
                message = "may only contain letters, digits, dot and underscore")
        String alias,

        @Schema(example = "Backend engineer. Coffee first, deploy later.")
        @Size(max = 280, message = "must be at most 280 characters")
        String bio
) {
}
