package dev.social.auth.config;

import dev.social.shared.security.JwtAuthenticationFilter;
import dev.social.shared.security.ProblemDetailAccessDeniedHandler;
import dev.social.shared.security.ProblemDetailAuthenticationEntryPoint;
import dev.social.shared.security.ResourceServerSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * What auth-service exposes without a token.
 *
 * <p>The chain itself comes from {@link ResourceServerSecurity}; the only thing
 * decided here is the list below - which is the point. Login and registration
 * have to be reachable by someone who has no token yet, and nothing else does.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(LoginPolicyProperties.class)
public class SecurityConfig {

    /**
     * Covers both {@code POST} and the deprecated {@code GET} form of login.
     * Everything else in this service, including {@code /api/auth/me} and the
     * account lookups, requires a valid token.
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
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
