package dev.social.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.social.shared.error.ErrorHandlingAutoConfiguration;
import dev.social.shared.error.ProblemDetailFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Supplies the JWT machinery. Each service still writes its own
 * {@code SecurityFilterChain} - which URLs are public differs per service, and
 * that decision should be visible in the service, not hidden in a shared jar.
 */
@AutoConfiguration(after = ErrorHandlingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecurityAutoConfiguration {

    /** BCrypt strength 10: the seed hashes were generated at this cost. */
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                                           ProblemDetailFactory problems,
                                                           ObjectMapper objectMapper) {
        return new JwtAuthenticationFilter(tokenProvider, problems, objectMapper);
    }

    /**
     * Stops Boot from also mounting the JWT filter on the raw servlet chain.
     *
     * <p>Boot auto-registers every {@code Filter} bean it finds. The filter is
     * already added to the Spring Security chain by each service's
     * {@code SecurityConfig}, so without this it would run <em>twice</em> per
     * request - once outside Spring Security, where a rejected token would be
     * written to the response before the security chain even starts.
     * {@code setEnabled(false)} disables the servlet registration only; the bean
     * itself stays available for injection.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint(
            ProblemDetailFactory problems, ObjectMapper objectMapper) {
        return new ProblemDetailAuthenticationEntryPoint(problems, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler(
            ProblemDetailFactory problems, ObjectMapper objectMapper) {
        return new ProblemDetailAccessDeniedHandler(problems, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
