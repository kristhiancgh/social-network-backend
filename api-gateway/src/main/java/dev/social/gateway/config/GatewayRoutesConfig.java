package dev.social.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where each path goes.
 *
 * <p>Written in Java rather than in {@code application.yml} on purpose. The
 * property namespace for Spring Cloud Gateway routes has moved more than once
 * between releases ({@code spring.cloud.gateway.routes} to
 * {@code spring.cloud.gateway.server.webflux.routes}), and a misspelled key
 * fails <em>silently</em> - the application starts and every request 404s. The
 * builder API is checked by the compiler and the routes can be read in one
 * screen.
 *
 * <h2>The gateway does not verify tokens</h2>
 * It forwards the {@code Authorization} header untouched and lets each service
 * decide. Validating here as well would mean the JWT secret has to live in five
 * places instead of four, and would create the classic trap where the gateway
 * says yes and the service behind it says no - or worse, the reverse.
 */
@Configuration
public class GatewayRoutesConfig {

    private final String authServiceUrl;
    private final String profileServiceUrl;
    private final String postServiceUrl;
    private final String likeServiceUrl;

    public GatewayRoutesConfig(
            @Value("${app.services.auth}") String authServiceUrl,
            @Value("${app.services.profile}") String profileServiceUrl,
            @Value("${app.services.post}") String postServiceUrl,
            @Value("${app.services.like}") String likeServiceUrl) {
        this.authServiceUrl = authServiceUrl;
        this.profileServiceUrl = profileServiceUrl;
        this.postServiceUrl = postServiceUrl;
        this.likeServiceUrl = likeServiceUrl;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("auth-service", route -> route
                        .path("/api/auth/**")
                        .uri(authServiceUrl))

                .route("profile-service", route -> route
                        .path("/api/profiles/**")
                        .uri(profileServiceUrl))

                .route("post-service", route -> route
                        .path("/api/posts/**")
                        .uri(postServiceUrl))

                .route("like-service", route -> route
                        .path("/api/likes/**")
                        .uri(likeServiceUrl))

                .route("post-service-websocket", route -> route
                        .path("/ws-posts/**", "/ws-posts")
                        .uri(postServiceUrl))

                .route("like-service-websocket", route -> route
                        .path("/ws/**", "/ws")
                        .uri(likeServiceUrl))

                .route("auth-service-docs", route -> route
                        .path("/v3/api-docs/auth-service")
                        .filters(filter -> filter.rewritePath(
                                "/v3/api-docs/auth-service", "/v3/api-docs"))
                        .uri(authServiceUrl))

                .route("profile-service-docs", route -> route
                        .path("/v3/api-docs/profile-service")
                        .filters(filter -> filter.rewritePath(
                                "/v3/api-docs/profile-service", "/v3/api-docs"))
                        .uri(profileServiceUrl))

                .route("post-service-docs", route -> route
                        .path("/v3/api-docs/post-service")
                        .filters(filter -> filter.rewritePath(
                                "/v3/api-docs/post-service", "/v3/api-docs"))
                        .uri(postServiceUrl))

                .route("like-service-docs", route -> route
                        .path("/v3/api-docs/like-service")
                        .filters(filter -> filter.rewritePath(
                                "/v3/api-docs/like-service", "/v3/api-docs"))
                        .uri(likeServiceUrl))

                .build();
    }
}
