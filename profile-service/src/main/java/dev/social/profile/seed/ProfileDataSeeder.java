package dev.social.profile.seed;

import dev.social.profile.profiles.domain.Profile;
import dev.social.profile.profiles.repositories.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One profile per demo account.
 *
 * <p>The {@code userId} values must match {@code AuthDataSeeder} in
 * auth-service exactly. They are repeated here rather than imported from a
 * shared module on purpose: a service that reaches into another service's
 * constants is coupled to it, and this is demo data, not a contract. The
 * duplication is small, deliberate, and mirrored in
 * {@code docker/postgres/seed/02-seed-profiledb.sql}.
 */
@Component
@ConditionalOnProperty(prefix = "app.seeder", name = "enabled", havingValue = "true")
public class ProfileDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileDataSeeder.class);

    private record SeedProfile(UUID profileId,
                               UUID userId,
                               String firstName,
                               String lastName,
                               LocalDate birthDate,
                               String alias,
                               String bio) {
    }

    private static final List<SeedProfile> SEED_PROFILES = List.of(
            new SeedProfile(
                    UUID.fromString("33333333-3333-3333-3333-333333330101"),
                    UUID.fromString("11111111-1111-1111-1111-111111110101"),
                    "John", "Doe", LocalDate.of(1992, 3, 14), "johnny",
                    "Backend engineer. Coffee first, deploy later."),
            new SeedProfile(
                    UUID.fromString("33333333-3333-3333-3333-333333330102"),
                    UUID.fromString("11111111-1111-1111-1111-111111110102"),
                    "Maria", "Garcia", LocalDate.of(1988, 11, 2), "mary_g",
                    "Product designer. I draw boxes and arrows for a living."),
            new SeedProfile(
                    UUID.fromString("33333333-3333-3333-3333-333333330103"),
                    UUID.fromString("11111111-1111-1111-1111-111111110103"),
                    "Li", "Chen", LocalDate.of(1995, 7, 21), "li_chen",
                    "Data engineer. Pipelines, parquet and patience."),
            new SeedProfile(
                    UUID.fromString("33333333-3333-3333-3333-333333330104"),
                    UUID.fromString("11111111-1111-1111-1111-111111110104"),
                    "Aisha", "Rossi", LocalDate.of(1999, 1, 9), "aisha_r",
                    "Frontend developer. Angular, accessibility and dark mode."),
            new SeedProfile(
                    UUID.fromString("33333333-3333-3333-3333-333333330105"),
                    UUID.fromString("11111111-1111-1111-1111-111111110105"),
                    "Kristhian", "Camilo", LocalDate.of(1994, 5, 30), "kris",
                    "Full stack developer. Building this network."));

    private final ProfileRepository profiles;

    public ProfileDataSeeder(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (SeedProfile seed : SEED_PROFILES) {
            if (profiles.existsByUserId(seed.userId())) {
                continue;
            }
            profiles.save(new Profile(
                    seed.profileId(),
                    seed.userId(),
                    seed.firstName(),
                    seed.lastName(),
                    seed.birthDate(),
                    seed.alias(),
                    seed.bio()));
            created++;
        }

        if (created > 0) {
            log.info("Seeded {} demo profile(s)", created);
        } else {
            log.info("Demo profiles already present, seeder skipped");
        }
    }
}
