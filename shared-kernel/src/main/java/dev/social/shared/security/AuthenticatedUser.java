package dev.social.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * The caller, as reconstructed from a verified JWT.
 *
 * <p>This is what lands in the {@code SecurityContext}, so a controller can
 * simply declare {@code @AuthenticationPrincipal AuthenticatedUser currentUser}
 * and get the id and username without another database round trip - the whole
 * reason post-service can stamp {@code author_username} onto a new post while
 * owning no user table of its own.
 *
 * <p>{@code userId} is the identity that matters. {@code username} is a
 * convenience copy: it can go stale if the account is renamed after the token
 * was issued, so never use it for authorisation decisions.
 */
public record AuthenticatedUser(

        UUID userId,

        String username,

        List<String> roles
) {

    public AuthenticatedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
