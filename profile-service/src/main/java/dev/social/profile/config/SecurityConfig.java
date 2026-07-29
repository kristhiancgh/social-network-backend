package dev.social.profile.config;

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
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * profile-service exposes nothing publicly.
 *
 * <p>The list is empty on purpose, and that is the whole security posture of
 * this service: it holds names and birth dates, so every read needs a token.
 * Docs and health remain reachable through
 * {@link ResourceServerSecurity#DOCUMENTATION_AND_HEALTH_PATHS}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {};

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
