package dev.social.profile.profiles.controllers;

import dev.social.profile.profiles.dto.ProfileResponse;
import dev.social.profile.profiles.dto.UpsertProfileRequest;
import dev.social.profile.profiles.services.ProfileService;
import dev.social.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Profiles", description = "First name, last name, birth date and alias")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "The authenticated user's profile",
            description = """
                    Backs the Profile screen.

                    Returns `PROFILE_NOT_FOUND` for an account that has registered
                    but not yet filled in its details - registration creates
                    credentials only. A client that gets a 404 here should send the
                    user to the profile form, not treat it as an error.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found"),
            @ApiResponse(responseCode = "404", description = "PROFILE_NOT_FOUND - not filled in yet",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProfileResponse> currentProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(profileService.findByUserId(currentUser.userId()));
    }

    @PutMapping(path = "/me",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create or update the authenticated user's profile",
            description = """
                    Idempotent: the first call creates the profile (201), later ones
                    update it (200). Which of the two happens is decided inside
                    `sp_upsert_profile`, in one statement, so two concurrent
                    first-time saves cannot both insert.

                    The owner comes from the token. There is no `userId` field in
                    the body, so this endpoint cannot be pointed at another user.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "201", description = "Profile created"),
            @ApiResponse(responseCode = "409", description = "ALIAS_ALREADY_EXISTS",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProfileResponse> upsertCurrentProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpsertProfileRequest request) {

        ProfileService.UpsertOutcome outcome =
                profileService.upsertOwnProfile(currentUser.userId(), request);

        if (outcome.created()) {
            return ResponseEntity
                    .created(UriComponentsBuilder.fromPath("/api/profiles/{userId}")
                            .buildAndExpand(currentUser.userId())
                            .toUri())
                    .body(outcome.profile());
        }
        return ResponseEntity.ok(outcome.profile());
    }

    @GetMapping(path = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Someone else's profile",
            description = "Profiles are public to any authenticated user - this is a social network.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found"),
            @ApiResponse(responseCode = "404", description = "PROFILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProfileResponse> findByUserId(
            @Parameter(description = "Account id", example = "11111111-1111-1111-1111-111111110101")
            @PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.findByUserId(userId));
    }

    @GetMapping(path = "/by-alias/{alias}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Look up a profile by its public alias")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found"),
            @ApiResponse(responseCode = "404", description = "PROFILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProfileResponse> findByAlias(
            @Parameter(description = "Public handle", example = "johnny")
            @PathVariable String alias) {
        return ResponseEntity.ok(profileService.findByAlias(alias));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Look up several profiles at once",
            description = """
                    Batch lookup by account id, so a screen showing many authors
                    makes one request instead of one per author.

                    Ids that have no profile are simply absent from the result -
                    a partial answer is more useful here than a 404 that discards
                    the profiles that were found.
                    """)
    public ResponseEntity<List<ProfileResponse>> findAllByUserIds(
            @Parameter(description = "Comma-separated account ids")
            @RequestParam(name = "userIds") List<UUID> userIds) {
        return ResponseEntity.ok(profileService.findAllByUserIds(userIds));
    }
}
