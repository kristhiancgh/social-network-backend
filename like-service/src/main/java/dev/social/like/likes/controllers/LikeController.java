package dev.social.like.likes.controllers;

import dev.social.like.likes.dto.LikeToggleResponse;
import dev.social.like.likes.dto.PostLikeSummary;
import dev.social.like.likes.dto.ToggleLikeRequest;
import dev.social.like.likes.services.LikeService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/likes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Likes", description = "Toggling likes and reading totals, with real-time updates")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Like or unlike a post",
            description = """
                    Toggles. Sending it twice returns the post to its original
                    state - there is no separate unlike endpoint, because a client
                    that had to choose would first need to know the current state,
                    and that state can change between reading and acting.

                    The whole cycle runs inside `sp_toggle_post_like`, which locks
                    the post's counter row first. Two users liking the same post at
                    the same instant therefore cannot lose a like, no matter how
                    many replicas of this service are running.

                    **After the transaction commits**, a `LikeChangedEvent` is
                    broadcast over the WebSocket to `/topic/likes` and to
                    `/topic/posts/{postId}/likes`, so every open browser updates
                    without polling. The response below is the same information,
                    returned directly so the clicking browser does not wait for its
                    own broadcast.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Toggled; new state returned"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LikeToggleResponse> toggle(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ToggleLikeRequest request) {

        return ResponseEntity.ok(likeService.toggle(request.postId(), currentUser));
    }

    @GetMapping(path = "/counts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Like totals for several posts at once",
            description = """
                    Returns one entry per requested post - total likes, and whether
                    the calling user is among them.

                    This is what the timeline calls after loading a page of posts:
                    two queries for twenty posts rather than forty. Posts nobody has
                    liked come back with `likeCount: 0` rather than being omitted.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "One summary per requested post"))
    public ResponseEntity<List<PostLikeSummary>> counts(
            @AuthenticationPrincipal AuthenticatedUser currentUser,

            @Parameter(description = "Comma-separated post ids",
                    example = "22222222-2222-2222-2222-222222220101,22222222-2222-2222-2222-222222220102")
            @RequestParam(name = "postIds") List<UUID> postIds) {

        return ResponseEntity.ok(likeService.summarise(postIds, currentUser.userId()));
    }

    @GetMapping(path = "/post/{postId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Like total for a single post")
    public ResponseEntity<PostLikeSummary> countForPost(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID postId) {

        return ResponseEntity.ok(likeService.summariseOne(postId, currentUser.userId()));
    }

    @GetMapping(path = "/post/{postId}/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Who liked a post",
            description = "Usernames, newest first. Backs a \"liked by\" tooltip.")
    public ResponseEntity<List<String>> likers(@PathVariable UUID postId) {
        return ResponseEntity.ok(likeService.findLikers(postId));
    }

    @PostMapping(path = "/maintenance/rebuild-counters", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Rebuild the like counters from the like rows",
            description = """
                    Administrative. Recomputes every total from `post_likes` via
                    `sp_rebuild_like_counters`.

                    Restricted to `ROLE_ADMIN`: it is cheap on this dataset but
                    scans the whole like table, and nothing a normal user does
                    should ever need it.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Number of counter rows after the rebuild"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Long> rebuildCounters() {
        return ResponseEntity.ok(likeService.rebuildCounters());
    }
}
