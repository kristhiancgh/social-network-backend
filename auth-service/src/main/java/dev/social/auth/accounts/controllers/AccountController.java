package dev.social.auth.accounts.controllers;

import dev.social.auth.accounts.dto.AccountResponse;
import dev.social.auth.accounts.dto.RegisterRequest;
import dev.social.auth.accounts.services.AccountService;
import dev.social.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Accounts", description = "Registration and account lookup")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping(path = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements   // public
    @Operation(
            summary = "Register a new account",
            description = """
                    Creates credentials only. The personal data the profile screen
                    shows - first name, last name, birth date, alias - belongs to
                    profile-service, so a client finishes signing up with a second
                    call to `PUT /api/profiles/me`.

                    Keeping the two apart is what lets authdb hold no personal data
                    at all: a breach of the credential store leaks no identities.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "409", description = "USERNAME_ALREADY_EXISTS / EMAIL_ALREADY_EXISTS",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        AccountResponse created = accountService.register(request);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/auth/accounts/{id}")
                        .buildAndExpand(created.id())
                        .toUri())
                .body(created);
    }

    @GetMapping(path = "/accounts/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Look up an account by id",
            description = "Returns the public view of an account. Never exposes the password hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AccountResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.findById(id));
    }

    @GetMapping(path = "/accounts/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "The authenticated account, read from the database",
            description = """
                    Unlike `/api/auth/me`, which only decodes the token, this loads
                    the row - so it reflects a change made after the token was
                    issued, such as the account being disabled.
                    """)
    public ResponseEntity<AccountResponse> currentAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(accountService.findById(currentUser.userId()));
    }
}
