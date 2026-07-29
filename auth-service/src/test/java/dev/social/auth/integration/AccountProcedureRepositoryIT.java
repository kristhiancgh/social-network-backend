package dev.social.auth.integration;

import dev.social.auth.accounts.repositories.AccountProcedureRepository;
import dev.social.auth.accounts.repositories.UserAccountRepository;
import dev.social.shared.error.SqlErrorSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Proves the Java-to-PL/pgSQL bridge works end to end.
 *
 * <p>This is the test that matters most in the module. Everything it exercises
 * fails only at runtime, never at compile time: that {@code CALL} against a
 * procedure with INOUT parameters really does come back as a one-row result
 * set, that the columns carry the parameter names, that a {@code uuid} maps to
 * {@link UUID}, and that a {@code RAISE EXCEPTION} inside PL/pgSQL still
 * carries its business code after Hibernate and Spring have wrapped it four
 * layers deep.
 *
 * <p>It has already earned its keep twice - it caught the driver's inability to
 * extract a UUID OUT parameter, and a real bug in
 * {@code sp_record_login_attempt} where ordering by {@code now()} (frozen for
 * the whole transaction) made the failure streak silently read as zero.
 */
@Transactional
class AccountProcedureRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private AccountProcedureRepository procedures;

    @Autowired
    private UserAccountRepository users;

    @Test
    @DisplayName("sp_register_user returns the generated id and grants ROLE_USER")
    void registersUserAndGrantsRole() {
        UUID userId = procedures.registerUser("alice", "alice@social.dev", "$2b$10$hash");

        assertThat(userId).isNotNull();
        assertThat(users.findById(userId))
                .hasValueSatisfying(account -> {
                    assertThat(account.getUsername()).isEqualTo("alice");
                    assertThat(account.getEmail()).isEqualTo("alice@social.dev");
                    assertThat(account.isEnabled()).isTrue();
                    assertThat(account.roleNames()).containsExactly("ROLE_USER");
                });
    }

    @Test
    @DisplayName("sp_register_user normalises username and email to lowercase")
    void normalisesCasing() {
        UUID userId = procedures.registerUser("  BoB  ", "  BoB@Social.DEV ", "$2b$10$hash");

        assertThat(users.findById(userId))
                .hasValueSatisfying(account -> {
                    assertThat(account.getUsername()).isEqualTo("bob");
                    assertThat(account.getEmail()).isEqualTo("bob@social.dev");
                });
    }

    @Test
    @DisplayName("sp_register_user raises USERNAME_ALREADY_EXISTS, recoverable via SqlErrorSupport")
    void rejectsDuplicateUsername() {
        procedures.registerUser("carol", "carol@social.dev", "$2b$10$hash");

        Throwable thrown = catchThrowable(
                () -> procedures.registerUser("carol", "other@social.dev", "$2b$10$hash"));

        assertThat(thrown).isNotNull();
        assertThat(SqlErrorSupport.raisedBusinessCode(thrown))
                .contains("USERNAME_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("sp_register_user raises EMAIL_ALREADY_EXISTS for a duplicate email")
    void rejectsDuplicateEmail() {
        procedures.registerUser("dave", "shared@social.dev", "$2b$10$hash");

        Throwable thrown = catchThrowable(
                () -> procedures.registerUser("erin", "shared@social.dev", "$2b$10$hash"));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown))
                .contains("EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("sp_record_login_attempt counts consecutive failures and resets on success")
    void tracksConsecutiveFailures() {
        procedures.registerUser("frank", "frank@social.dev", "$2b$10$hash");

        assertThat(procedures.recordLoginAttempt("frank", false, "BAD_CREDENTIALS", "10.0.0.1", "junit", 15))
                .isEqualTo(1);
        assertThat(procedures.recordLoginAttempt("frank", false, "BAD_CREDENTIALS", "10.0.0.1", "junit", 15))
                .isEqualTo(2);

        assertThat(procedures.recordLoginAttempt("frank", true, null, "10.0.0.1", "junit", 15))
                .isZero();

        assertThat(procedures.recordLoginAttempt("frank", false, "BAD_CREDENTIALS", "10.0.0.1", "junit", 15))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sp_record_login_attempt ignores failures older than the window, so a lockout expires")
    void forgetsFailuresOutsideTheWindow() {
        procedures.registerUser("grace", "grace@social.dev", "$2b$10$hash");

        procedures.recordLoginAttempt("grace", false, "BAD_CREDENTIALS", "10.0.0.1", "junit", 15);
        procedures.recordLoginAttempt("grace", false, "BAD_CREDENTIALS", "10.0.0.1", "junit", 15);

        int withinExpiredWindow = procedures.recordLoginAttempt(
                "grace", false, "BAD_CREDENTIALS", "10.0.0.1", "junit", 0);

        assertThat(withinExpiredWindow).isZero();
    }

    @Test
    @DisplayName("sp_record_login_attempt audits attempts for accounts that do not exist")
    void auditsUnknownUsernames() {
        int failures = procedures.recordLoginAttempt(
                "ghost", false, "USER_NOT_FOUND", "203.0.113.9", "curl/8", 15);

        assertThat(failures).isEqualTo(1);
    }

    @Test
    @DisplayName("SqlErrorSupport ignores failures that did not come from a procedure")
    void leavesOrdinaryFailuresAlone() {
        Optional<String> code = SqlErrorSupport.raisedBusinessCode(new IllegalStateException("boom"));
        assertThat(code).isEmpty();
    }

    @Test
    @DisplayName("username format constraint is enforced by the database")
    void rejectsInvalidUsernameFormat() {
        assertThatThrownBy(() -> procedures.registerUser("x", "x@social.dev", "$2b$10$hash"))
                .isNotNull();
    }
}
