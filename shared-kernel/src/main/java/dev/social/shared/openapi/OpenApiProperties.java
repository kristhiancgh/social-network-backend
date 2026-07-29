package dev.social.shared.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-service Swagger metadata, bound from {@code app.openapi.*}.
 *
 * <p>Only the prose differs between services; the security scheme, the servers
 * list and the bearer-token wiring are identical everywhere and live in
 * {@link OpenApiAutoConfiguration}.
 */
@ConfigurationProperties(prefix = "app.openapi")
public record OpenApiProperties(

        @DefaultValue("Social Network API")
        String title,

        @DefaultValue("1.0.0")
        String version,

        @DefaultValue("")
        String description,

        @DefaultValue("http://localhost:8080")
        String gatewayUrl
) {
}
