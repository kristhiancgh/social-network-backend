package dev.social.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Brute-force policy, bound from {@code app.login-policy.*}.
 *
 * @param maxFailedAttempts failures within the window before the account is
 *                          refused; {@code 0} disables the lockout entirely
 * @param windowMinutes     how far back failures count. The lockout has no
 *                          unlock endpoint by design - it lapses once the
 *                          window rolls past, so a legitimate user who mistyped
 *                          five times is inconvenienced for minutes, not until
 *                          an administrator intervenes.
 */
@ConfigurationProperties(prefix = "app.login-policy")
public record LoginPolicyProperties(

        @DefaultValue("5")
        int maxFailedAttempts,

        @DefaultValue("15")
        int windowMinutes
) {

    public boolean lockoutEnabled() {
        return maxFailedAttempts > 0;
    }
}
