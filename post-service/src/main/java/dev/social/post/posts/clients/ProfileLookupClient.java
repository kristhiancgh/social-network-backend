package dev.social.post.posts.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an author's public alias from profile-service.
 *
 * <p>This is the only place post-service talks to another service, and it does
 * so on the <em>write</em> path only - once per published post, never while
 * rendering a timeline. That asymmetry is the whole point of denormalising
 * {@code author_alias} onto the post: pay one call when publishing, so listing
 * twenty posts costs zero.
 *
 * <h2>Failure is not an error</h2>
 * If profile-service is slow, down, or simply has no profile for this user yet
 * (perfectly normal - registration creates credentials before details), the
 * lookup returns empty and the post is published with a null alias. Refusing to
 * publish because a <em>decorative</em> field could not be fetched would make
 * post-service inherit profile-service's availability, which is exactly the
 * coupling splitting them was meant to remove.
 *
 * <p>The caller's own token is forwarded rather than a service account: the
 * request is made on behalf of that user, and profile-service applies the same
 * rules it would to a direct call.
 */
@Component
public class ProfileLookupClient {

    private static final Logger log = LoggerFactory.getLogger(ProfileLookupClient.class);

    private final RestClient restClient;

    public ProfileLookupClient(RestClient.Builder builder,
                               @Value("${app.clients.profile-service.base-url}") String baseUrl,
                               @Value("${app.clients.profile-service.timeout-ms:1500}") long timeoutMs) {

        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * @param bearerToken the caller's raw JWT, forwarded as-is
     * @return the alias, or empty when it could not be obtained for any reason
     */
    public Optional<String> findAlias(UUID userId, String bearerToken) {
        try {
            ProfileSummary profile = restClient.get()
                    .uri("/api/profiles/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(ProfileSummary.class);

            return Optional.ofNullable(profile).map(ProfileSummary::alias);

        } catch (RuntimeException exception) {
            log.debug("Could not resolve alias for user {}: {}", userId, exception.getMessage());
            return Optional.empty();
        }
    }

    private record ProfileSummary(String alias) {
    }
}
