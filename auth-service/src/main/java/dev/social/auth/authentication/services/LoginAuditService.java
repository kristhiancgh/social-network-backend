package dev.social.auth.authentication.services;

import dev.social.auth.accounts.repositories.AccountProcedureRepository;
import dev.social.auth.config.LoginPolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records login attempts.
 *
 * <p>A separate bean with {@link Propagation#REQUIRES_NEW} for one specific
 * reason: <b>a failed login has to leave a trace even though it ends in an
 * exception</b>. If the audit ran in the caller's transaction, the
 * {@code INVALID_CREDENTIALS} thrown right afterwards would mark that
 * transaction rollback-only and the audit row would vanish - deleting exactly
 * the evidence a brute-force attempt produces.
 *
 * <p>It also has to be a different bean, not just a different method. Spring's
 * transaction support is proxy-based, so {@code this.record(...)} from inside
 * the same class bypasses the proxy and the new propagation would never apply.
 */
@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    private final AccountProcedureRepository procedures;
    private final LoginPolicyProperties policy;

    public LoginAuditService(AccountProcedureRepository procedures, LoginPolicyProperties policy) {
        this.procedures = procedures;
        this.policy = policy;
    }

    /**
     * @return consecutive failures within the policy window, {@code 0} on success
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordAttempt(String username,
                             boolean successful,
                             String failureCode,
                             String ipAddress,
                             String userAgent) {

        int consecutiveFailures = procedures.recordLoginAttempt(
                username, successful, failureCode, ipAddress, userAgent, policy.windowMinutes());

        if (policy.lockoutEnabled() && consecutiveFailures >= policy.maxFailedAttempts()) {
            log.warn("Account '{}' has {} consecutive failed logins from {} - now locked for up to {} minutes",
                    username, consecutiveFailures, ipAddress, policy.windowMinutes());
        }
        return consecutiveFailures;
    }
}
