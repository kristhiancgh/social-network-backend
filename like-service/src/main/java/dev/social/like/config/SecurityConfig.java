package dev.social.like.config;

import dev.social.shared.security.JwtAuthenticationFilter;
import dev.social.shared.security.ProblemDetailAccessDeniedHandler;
import dev.social.shared.security.ProblemDetailAuthenticationEntryPoint;
import dev.social.shared.security.ResourceServerSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * like-service is the only service with a public path that is not documentation.
 *
 * <p>{@code /ws/**} has to be reachable without a bearer token, because a
 * browser cannot attach an {@code Authorization} header to a WebSocket
 * handshake. It is <b>not</b> unauthenticated: the very first STOMP frame must
 * carry a valid token or {@code StompAuthenticationInterceptor} drops the
 * connection before it can subscribe to anything. Authentication is moved one
 * frame later, not removed.
 *
 * <p>{@link EnableMethodSecurity} is switched on for the {@code @PreAuthorize}
 * guarding the counter-rebuild endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/ws",
            "/ws/**",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                          ProblemDetailAccessDeniedHandler accessDeniedHandler,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return ResourceServerSecurity.statelessChain(
                http,
                jwtAuthenticationFilter,
                authenticationEntryPoint,
                accessDeniedHandler,
                corsConfigurationSource(),
                PUBLIC_PATHS);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return ResourceServerSecurity.corsFor(allowedOrigins);
    }
}
