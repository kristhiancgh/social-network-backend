package dev.social.auth.seed;

import dev.social.auth.accounts.domain.Role;
import dev.social.auth.accounts.domain.UserAccount;
import dev.social.auth.accounts.repositories.RoleRepository;
import dev.social.auth.accounts.repositories.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Creates the demo accounts on startup, as the brief requires.
 *
 * <h2>Why the ids are hard-coded</h2>
 * Four services own four separate databases and cannot join across them, so
 * profiledb, postdb and likedb refer to a user by literal UUID. Every seeder in
 * this project agrees on the same five values; change one here and the demo
 * data stops lining up. They mirror {@code docker/postgres/seed/01-seed-authdb.sql}
 * exactly.
 *
 * <h2>Why it hashes at runtime</h2>
 * The password goes through the same {@link PasswordEncoder} bean the login
 * path uses, so the seeded accounts cannot drift from the production hashing
 * configuration. Pasting a pre-computed hash into a migration would break
 * silently the day the BCrypt cost changes.
 *
 * <p>Idempotent: an account that is already there is left untouched, so a
 * restart neither fails nor duplicates.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.seeder", name = "enabled", havingValue = "true")
public class AuthDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDataSeeder.class);

    /** Shared by every demo account. Documented in the README and the PDF. */
    private static final String DEFAULT_PASSWORD = "Password123!";

    private record SeedAccount(UUID id, String username, String email, boolean admin) {
    }

    private static final List<SeedAccount> SEED_ACCOUNTS = List.of(
            new SeedAccount(UUID.fromString("11111111-1111-1111-1111-111111110101"),
                    "jdoe", "john.doe@social.dev", false),
            new SeedAccount(UUID.fromString("11111111-1111-1111-1111-111111110102"),
                    "mgarcia", "maria.garcia@social.dev", false),
            new SeedAccount(UUID.fromString("11111111-1111-1111-1111-111111110103"),
                    "lchen", "li.chen@social.dev", false),
            new SeedAccount(UUID.fromString("11111111-1111-1111-1111-111111110104"),
                    "arossi", "aisha.rossi@social.dev", false),
            new SeedAccount(UUID.fromString("11111111-1111-1111-1111-111111110105"),
                    "kcamilo", "kristhian.camilo@social.dev", true));

    private final UserAccountRepository accounts;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public AuthDataSeeder(UserAccountRepository accounts,
                          RoleRepository roles,
                          PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role userRole = requireRole(Role.ROLE_USER);
        Role adminRole = requireRole(Role.ROLE_ADMIN);

        // Hashed once, not per account: BCrypt at cost 10 takes ~100 ms, and
        // five identical passwords do not need five hashes. Each account still
        // ends up with the same digest here only because the seed password is
        // shared - real registrations always hash individually, salt included.
        String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);

        int created = 0;
        for (SeedAccount seed : SEED_ACCOUNTS) {
            if (accounts.existsById(seed.id())) {
                continue;
            }
            UserAccount account = new UserAccount(
                    seed.id(), seed.username(), seed.email(), passwordHash);
            account.grant(userRole);
            if (seed.admin()) {
                account.grant(adminRole);
            }
            accounts.save(account);
            created++;
        }

        if (created > 0) {
            log.info("Seeded {} demo account(s). Every one logs in with password: {}",
                    created, DEFAULT_PASSWORD);
        } else {
            log.info("Demo accounts already present, seeder skipped");
        }
    }

    private Role requireRole(String name) {
        return roles.findByName(name).orElseThrow(() -> new IllegalStateException(
                "Role %s is missing. Migration V1__auth_schema.sql should have inserted it."
                        .formatted(name)));
    }
}
