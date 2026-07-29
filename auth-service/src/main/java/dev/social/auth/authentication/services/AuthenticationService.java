package dev.social.auth.authentication.services;

import dev.social.auth.accounts.domain.UserAccount;
import dev.social.auth.accounts.mappers.AccountMapper;
import dev.social.auth.accounts.repositories.UserAccountRepository;
import dev.social.auth.authentication.dto.ClientContext;
import dev.social.auth.authentication.dto.LoginRequest;
import dev.social.auth.authentication.dto.LoginResponse;
import dev.social.auth.authentication.exceptions.AuthenticationErrorCode;
import dev.social.auth.authentication.repositories.LoginAuditRepository;
import dev.social.auth.config.LoginPolicyProperties;
import dev.social.shared.error.BusinessRuleException;
import dev.social.shared.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Verifies credentials and issues the access token.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The audit write has to
 * survive the exception that a failed login throws, so it runs in its own
 * transaction inside {@link LoginAuditService}; wrapping the whole method would
 * put that write back under a rollback-only transaction and quietly erase it.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);


    private static final String REASON_USER_NOT_FOUND = "USER_NOT_FOUND";
    private static final String REASON_BAD_PASSWORD = "BAD_PASSWORD";
    private static final String REASON_DISABLED = "ACCOUNT_DISABLED";
    private static final String REASON_LOCKED = "ACCOUNT_LOCKED";


    private static final String DUMMY_HASH =
            "$2b$10$pakZVvpUH3Ox87IWTd1yX.7tZrBCCDMWsH5mu.pMcjHTYvw3ZpI1C";

    private final UserAccountRepository accounts;
    private final LoginAuditRepository auditTrail;
    private final LoginAuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AccountMapper mapper;
    private final LoginPolicyProperties policy;

    public AuthenticationService(UserAccountRepository accounts,
                                 LoginAuditRepository auditTrail,
                                 LoginAuditService auditService,
                                 PasswordEncoder passwordEncoder,
                                 JwtTokenProvider tokenProvider,
                                 AccountMapper mapper,
                                 LoginPolicyProperties policy) {
        this.accounts = accounts;
        this.auditTrail = auditTrail;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.mapper = mapper;
        this.policy = policy;
    }

    public LoginResponse login(LoginRequest request, ClientContext client) {
        String username = request.username().trim().toLowerCase();

        rejectIfLockedOut(username, client);

        Optional<UserAccount> found = accounts.findByUsername(username);

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            auditService.recordAttempt(username, false, REASON_USER_NOT_FOUND,
                    client.ipAddress(), client.userAgent());
            throw invalidCredentials();
        }

        UserAccount account = found.get();

        if (!account.isEnabled()) {
            auditService.recordAttempt(username, false, REASON_DISABLED,
                    client.ipAddress(), client.userAgent());
            throw new BusinessRuleException(AuthenticationErrorCode.ACCOUNT_DISABLED,
                    "This account has been disabled");
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            int failures = auditService.recordAttempt(username, false, REASON_BAD_PASSWORD,
                    client.ipAddress(), client.userAgent());

            if (policy.lockoutEnabled() && failures >= policy.maxFailedAttempts()) {
                throw lockedOut();
            }
            throw invalidCredentials();
        }

        auditService.recordAttempt(username, true, null, client.ipAddress(), client.userAgent());

        JwtTokenProvider.IssuedToken issued =
                tokenProvider.issue(account.getId(), account.getUsername(), account.roleNames());

        log.info("Login succeeded for {} ({})", account.getUsername(), account.getId());

        return new LoginResponse(
                issued.token(),
                LoginResponse.BEARER,
                issued.expiresInSeconds(),
                issued.expiresAt(),
                mapper.toSummary(account));
    }

    private void rejectIfLockedOut(String username, ClientContext client) {
        if (!policy.lockoutEnabled()) {
            return;
        }
        Instant since = Instant.now().minus(Duration.ofMinutes(policy.windowMinutes()));
        long failures = auditTrail.countRecentFailures(username, since);

        if (failures >= policy.maxFailedAttempts()) {
            auditService.recordAttempt(username, false, REASON_LOCKED,
                    client.ipAddress(), client.userAgent());
            throw lockedOut();
        }
    }

    private BusinessRuleException invalidCredentials() {
        return new BusinessRuleException(AuthenticationErrorCode.INVALID_CREDENTIALS,
                "Invalid username or password");
    }

    private BusinessRuleException lockedOut() {
        return new BusinessRuleException(AuthenticationErrorCode.ACCOUNT_LOCKED,
                "Too many failed attempts. Try again in %d minutes."
                        .formatted(policy.windowMinutes()));
    }
}
