package dev.social.shared.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from {@code app.jwt.*}.
 *
 * <p>Every service reads the <em>same</em> secret: auth-service signs, the
 * other three verify. That is the trade-off of a shared-secret (HMAC) setup -
 * simple to run, but any service that can verify a token can also mint one. For
 * a real deployment this would move to an asymmetric key so only auth-service
 * holds the private half; the switch touches this class and
 * {@link JwtTokenProvider} and nothing else.
 *
 * @param secret       HMAC key. Must be at least 32 bytes for HS256 - jjwt
 *                     refuses anything weaker, which is the behaviour we want:
 *                     a short secret is a forgeable token.
 * @param issuer       expected {@code iss} claim; tokens from anywhere else are
 *                     rejected even if the signature checks out
 * @param accessTokenTtl how long a token stays valid
 * @param clockSkew    tolerance for clock drift between containers when
 *                     checking {@code exp} and {@code nbf}
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "app.jwt.secret must be configured")
        String secret,

        @DefaultValue("social-network")
        String issuer,

        @DefaultValue("2h")
        Duration accessTokenTtl,

        @DefaultValue("30s")
        Duration clockSkew
) {

    public static final int MINIMUM_SECRET_BYTES = 32;
}
