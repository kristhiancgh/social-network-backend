package dev.social.auth.authentication.controllers;

import dev.social.auth.authentication.dto.ClientContext;
import dev.social.auth.authentication.dto.LoginRequest;
import dev.social.auth.authentication.dto.LoginResponse;
import dev.social.auth.authentication.services.AuthenticationService;
import dev.social.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "Credential verification and JWT issuing")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    // -------------------------------------------------------------------------
    //  POST /api/auth/login  - the endpoint clients should use
    // -------------------------------------------------------------------------

    @PostMapping(path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(
            summary = "Log in and obtain a JWT",
            description = """
                    Exchanges a username and password for a signed access token.

                    Send the returned token on every subsequent request as:
                    `Authorization: Bearer <accessToken>`

                    Failures answer `INVALID_CREDENTIALS` whether the account does
                    not exist or the password is wrong - distinguishing them would
                    let anyone enumerate valid usernames.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED - too many failed attempts",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {

        LoginResponse response = authenticationService.login(request, ClientContext.from(httpRequest));
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    //  GET /api/auth/login  - required by the brief, kept deprecated
    // -------------------------------------------------------------------------

    /**
     * Login over GET, with the credentials as query parameters.
     *
     * <p>Provided because the specification asks for it, and marked deprecated
     * because it is unsafe in a way that has nothing to do with HTTPS. A query
     * string is not part of the encrypted body - it is the URL, so the password
     * ends up in the browser's history and autocomplete, in the access log of
     * every proxy and gateway on the path, and in the {@code Referer} header
     * sent to any third-party resource the next page loads.
     *
     * <p>{@code Cache-Control: no-store} at least keeps the response out of
     * caches. Nothing can pull the password back out of the logs it already
     * reached. Point real clients at {@code POST /api/auth/login}.
     */
    @GetMapping(path = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Deprecated(since = "1.0.0")
    @Operation(
            summary = "[DEPRECATED] Log in over GET",
            deprecated = true,
            description = """
                    Included only to satisfy the literal requirement "login con JWT (GET)".

                    **Do not use this in a real client.** Credentials travel in the
                    query string, which means they are written to browser history,
                    proxy and gateway access logs, and the `Referer` header. HTTPS
                    does not help: the URL is logged at both ends in clear text.

                    Use `POST /api/auth/login`, which carries the credentials in the
                    request body, instead.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LoginResponse> loginViaGet(
            @Parameter(description = "Account username", example = "jdoe", required = true)
            @RequestParam @NotBlank String username,

            @Parameter(description = "Account password", example = "Password123!", required = true)
            @RequestParam @NotBlank String password,

            HttpServletRequest httpRequest) {

        LoginResponse response = authenticationService.login(
                new LoginRequest(username, password), ClientContext.from(httpRequest));

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .body(response);
    }

    // -------------------------------------------------------------------------
    //  GET /api/auth/me
    // -------------------------------------------------------------------------

    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Identity behind the current token",
            description = """
                    Echoes back what the presented JWT actually claims, without
                    touching the database. Useful for verifying a token is still
                    accepted and for debugging what a client is really sending.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED / INVALID_TOKEN / EXPIRED_TOKEN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LoginResponse.AuthenticatedUserSummary> currentUser(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(new LoginResponse.AuthenticatedUserSummary(
                currentUser.userId(), currentUser.username(), currentUser.roles()));
    }
}
