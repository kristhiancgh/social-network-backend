package dev.social.post.posts.controllers;

import dev.social.post.posts.clients.ProfileLookupClient;
import dev.social.post.posts.dto.CreatePostRequest;
import dev.social.post.posts.dto.PostResponse;
import dev.social.post.posts.services.PostService;
import dev.social.shared.security.AuthenticatedUser;
import dev.social.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Posts", description = "Publishing and reading the timeline")
public class PostController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PostService postService;
    private final ProfileLookupClient profileLookup;

    public PostController(PostService postService, ProfileLookupClient profileLookup) {
        this.postService = postService;
        this.profileLookup = profileLookup;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List the timeline",
            description = """
                    Posts newest first, paginated.

                    `includeOwn` defaults to **false**, so the result is "everyone
                    else's posts" - what the Posts screen shows. Set it to `true`
                    to include your own.

                    Like counts are not part of this payload. They come from
                    `GET /api/likes/counts` and stay fresh over the WebSocket; a
                    number embedded here would be stale before it reached the
                    browser.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of posts"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PageResponse<PostResponse>> timeline(
            @AuthenticationPrincipal AuthenticatedUser currentUser,

            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size, at most 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,

            @Parameter(description = "Include the caller's own posts")
            @RequestParam(defaultValue = "false") boolean includeOwn) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        UUID excluded = includeOwn ? null : currentUser.userId();

        return ResponseEntity.ok(postService.findTimeline(excluded, pageable));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Publish a post",
            description = """
                    The message is the only input. The author is taken from the
                    token and the publication date is stamped by the database with
                    `now()`, as the brief requires.

                    Publishing the identical message twice within 30 seconds is
                    refused with `DUPLICATE_POST` - that is almost always a double
                    submit rather than an intention.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Published"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_POST",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR / EMPTY_MESSAGE",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreatePostRequest request,
            HttpServletRequest httpRequest) {

        // Best-effort decoration. If profile-service cannot answer, the post is
        // still published - just without an alias.
        String alias = bearerToken(httpRequest)
                .flatMap(token -> profileLookup.findAlias(currentUser.userId(), token))
                .orElse(null);

        PostResponse created = postService.create(currentUser, alias, request);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/posts/{id}")
                        .buildAndExpand(created.id())
                        .toUri())
                .body(created);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read one post")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PostResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(postService.findById(id));
    }

    @GetMapping(path = "/author/{authorId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List one author's posts, newest first")
    public ResponseEntity<PageResponse<PostResponse>> findByAuthor(
            @PathVariable UUID authorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(postService.findByAuthor(authorId, pageable));
    }

    @DeleteMapping(path = "/{id}")
    @Operation(
            summary = "Delete your own post",
            description = """
                    Soft delete: the row stays so the likes recorded against it in
                    likedb are not orphaned, but it disappears from every read.

                    Idempotent - deleting an already-deleted post is still 204.
                    Deleting someone else's is `NOT_POST_OWNER` (403), checked
                    inside the database under a row lock.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "NOT_POST_OWNER",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "POST_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable UUID id) {
        postService.delete(id, currentUser.userId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private java.util.Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(header.substring("Bearer ".length()).trim());
    }
}
