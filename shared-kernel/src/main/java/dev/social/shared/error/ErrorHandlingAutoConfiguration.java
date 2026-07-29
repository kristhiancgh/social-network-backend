package dev.social.shared.error;

import dev.social.shared.web.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Wires the shared error contract into any service that puts shared-kernel on
 * its classpath.
 *
 * <p>Registered through {@code AutoConfiguration.imports} rather than component
 * scanning. A service scans its own package ({@code dev.social.post}, ...), so
 * widening the scan to {@code dev.social.shared} would be the alternative - and
 * that would silently pull in every future shared bean, wanted or not.
 * Auto-configuration keeps the contract explicit and lets a service override
 * any single bean just by declaring its own.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

    /**
     * The service name is stamped into every error payload. Falls back rather
     * than failing, so a bare test slice without {@code spring.application.name}
     * still starts.
     */
    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailFactory problemDetailFactory(
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        return new ProblemDetailFactory(serviceName);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        return new GlobalExceptionHandler(problemDetailFactory);
    }

    /**
     * Registered explicitly instead of as a plain {@code @Bean} so the order can
     * be pinned. The trace id has to exist before anything else can log.
     */
    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
