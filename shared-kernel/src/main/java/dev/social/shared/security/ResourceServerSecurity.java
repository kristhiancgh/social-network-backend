package dev.social.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The stateless JWT filter chain every service shares.
 *
 * <p>What is factored out here is only the part that must be identical
 * everywhere - no sessions, no CSRF, no form login, RFC 7807 on rejection.
 * <b>Which URLs are public stays with each service</b>, passed in as an
 * argument, because that is the one security decision that genuinely differs
 * and the one that must be obvious when reading the service rather than buried
 * in a shared jar.
 *
 * <p>A helper class rather than an auto-configuration: a service that needs
 * something unusual writes its own {@code SecurityFilterChain} and simply does
 * not call this, with no {@code @ConditionalOnMissingBean} subtleties to reason
 * about.
 */
public final class ResourceServerSecurity {

    public static final String[] DOCUMENTATION_AND_HEALTH_PATHS = {
            "/docs",
            "/docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
    };

    private ResourceServerSecurity() {
    }

    /**
     * Builds the chain.
     *
     * @param servicePublicPaths paths this particular service exposes without a
     *                           token, on top of docs and health
     */
    public static SecurityFilterChain statelessChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     ProblemDetailAuthenticationEntryPoint entryPoint,
                                                     ProblemDetailAccessDeniedHandler accessDeniedHandler,
                                                     CorsConfigurationSource corsSource,
                                                     String... servicePublicPaths) throws Exception {

        List<String> publicPaths = new ArrayList<>(List.of(DOCUMENTATION_AND_HEALTH_PATHS));
        publicPaths.addAll(List.of(servicePublicPaths));

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(corsSource))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    /**
     * CORS for a browser client holding a bearer token.
     *
     * <p>Origins are always listed explicitly. {@code "*"} is not merely lax -
     * the spec forbids combining it with {@code allowCredentials}, so it would
     * silently break the very requests it is meant to allow.
     */
    public static CorsConfigurationSource corsFor(List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
        configuration.setExposedHeaders(List.of("X-Trace-Id", "Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
