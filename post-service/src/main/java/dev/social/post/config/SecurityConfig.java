package dev.social.post.config;

import dev.social.shared.security.JwtAuthenticationFilter;
import dev.social.shared.security.ProblemDetailAccessDeniedHandler;
import dev.social.shared.security.ProblemDetailAuthenticationEntryPoint;
import dev.social.shared.security.ResourceServerSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * Reading the timeline requires a token - this is a network of registered users,
 * not a public feed.
 *
 * <p>The one exception is {@code /ws-posts}, and it is not really an exception:
 * a browser cannot attach an Authorization header to a WebSocket handshake, so
 * the handshake is left open and the very first STOMP frame must carry a valid
 * token or {@code StompAuthenticationInterceptor} drops the connection before
 * it can subscribe to anything. Authentication moves one frame later, it is not
 * removed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/ws-posts",
            "/ws-posts/**",
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

    /**
     * Boot only auto-configures a {@code RestClient.Builder} when
     * spring-boot-starter-web brought the pieces in; declaring it explicitly
     * keeps {@code ProfileLookupClient} constructible in test slices too.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
