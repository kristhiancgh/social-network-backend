package dev.social.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One rejected field inside a validation error.
 *
 * <p>Carrying the offending value back would echo whatever the client sent
 * straight into logs and error trackers, so only the field name and the
 * message travel.
 */
@Schema(name = "FieldViolation", description = "A single field that failed validation")
public record FieldViolation(

        @Schema(description = "Path of the rejected field", example = "message")
        String field,

        @Schema(description = "Why it was rejected", example = "must not be blank")
        String message
) {
}
