package dev.social.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Gives all four services the same Swagger UI, reachable at {@code /docs}
 * (the path itself is set per service via {@code springdoc.swagger-ui.path}).
 *
 * <p>The important part is {@code addSecurityItem}: it declares the bearer
 * scheme globally, which is what puts the <b>Authorize</b> button in the UI.
 * Without it every secured endpoint returns 401 from Swagger and the docs are
 * only good for reading, not for trying.
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI socialNetworkOpenApi(OpenApiProperties properties) {

        SecurityScheme bearerScheme = new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the `accessToken` returned by POST /api/auth/login. "
                        + "Swagger adds the `Bearer ` prefix for you.");

        return new OpenAPI()
                .info(new Info()
                        .title(properties.title())
                        .version(properties.version())
                        .description(properties.description())
                        .contact(new Contact().name("Social Network Team"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url(properties.gatewayUrl()).description("API gateway")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
