package dev.social.profile.integration;

import dev.social.profile.profiles.repositories.ProfileProcedureRepository;
import dev.social.profile.profiles.repositories.ProfileRepository;
import dev.social.shared.error.SqlErrorSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Exercises {@code sp_upsert_profile}. */
@Transactional
class ProfileProcedureRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private ProfileProcedureRepository procedures;

    @Autowired
    private ProfileRepository profiles;

    private static UUID newUser() {
        return UUID.randomUUID();
    }

    private static String uniqueAlias(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6).replace("-", "");
    }

    @Test
    @DisplayName("first save inserts and reports created = true")
    void insertsOnFirstSave() {
        UUID userId = newUser();
        String alias = uniqueAlias("first");

        ProfileProcedureRepository.UpsertResult result = procedures.upsertProfile(
                userId, "John", "Doe", LocalDate.of(1992, 3, 14), alias, "A bio");

        assertThat(result.created()).isTrue();
        assertThat(profiles.findByUserId(userId)).hasValueSatisfying(profile -> {
            assertThat(profile.getFirstName()).isEqualTo("John");
            assertThat(profile.getLastName()).isEqualTo("Doe");
            assertThat(profile.getBirthDate()).isEqualTo(LocalDate.of(1992, 3, 14));
            assertThat(profile.getAlias()).isEqualTo(alias);
        });
    }

    @Test
    @DisplayName("second save updates the same row and reports created = false")
    void updatesOnSecondSave() {
        UUID userId = newUser();
        String alias = uniqueAlias("second");

        ProfileProcedureRepository.UpsertResult first = procedures.upsertProfile(
                userId, "John", "Doe", LocalDate.of(1992, 3, 14), alias, null);
        ProfileProcedureRepository.UpsertResult second = procedures.upsertProfile(
                userId, "Johnathan", "Doe", LocalDate.of(1992, 3, 14), alias, "Updated");

        assertThat(second.created()).isFalse();
        assertThat(second.profileId()).isEqualTo(first.profileId());
        assertThat(profiles.findByUserId(userId)).hasValueSatisfying(profile -> {
            assertThat(profile.getFirstName()).isEqualTo("Johnathan");
            assertThat(profile.getBio()).isEqualTo("Updated");
        });
    }

    @Test
    @DisplayName("re-saving an unchanged profile is not treated as an alias conflict")
    void keepingYourOwnAliasIsAllowed() {
        UUID userId = newUser();
        String alias = uniqueAlias("keep");
        procedures.upsertProfile(userId, "Li", "Chen", LocalDate.of(1995, 7, 21), alias, null);

        ProfileProcedureRepository.UpsertResult again = procedures.upsertProfile(
                userId, "Li", "Chen", LocalDate.of(1995, 7, 21), alias, "New bio");

        assertThat(again.created()).isFalse();
    }

    @Test
    @DisplayName("claiming somebody else's alias raises ALIAS_ALREADY_EXISTS")
    void rejectsTakenAlias() {
        String alias = uniqueAlias("taken");
        procedures.upsertProfile(newUser(), "Maria", "Garcia", LocalDate.of(1988, 11, 2), alias, null);

        Throwable thrown = catchThrowable(() -> procedures.upsertProfile(
                newUser(), "Someone", "Else", LocalDate.of(1990, 1, 1), alias, null));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown)).contains("ALIAS_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("the alias conflict check is case-insensitive")
    void aliasConflictIgnoresCase() {
        String alias = uniqueAlias("case");
        procedures.upsertProfile(newUser(), "Maria", "Garcia", LocalDate.of(1988, 11, 2), alias, null);

        Throwable thrown = catchThrowable(() -> procedures.upsertProfile(
                newUser(), "Other", "Person", LocalDate.of(1990, 1, 1), alias.toUpperCase(), null));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown)).contains("ALIAS_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("a future birth date is refused by the database constraint")
    void rejectsFutureBirthDate() {
        Throwable thrown = catchThrowable(() -> procedures.upsertProfile(
                newUser(), "Time", "Traveller", LocalDate.now().plusYears(1),
                uniqueAlias("future"), null));

        assertThat(thrown).isNotNull();
    }

    @Test
    @DisplayName("age is computed on read, not stored")
    void computesAge() {
        UUID userId = newUser();
        LocalDate birthDate = LocalDate.now().minusYears(30).minusDays(1);
        procedures.upsertProfile(userId, "Age", "Test", birthDate, uniqueAlias("age"), null);

        assertThat(profiles.findByUserId(userId))
                .hasValueSatisfying(profile -> assertThat(profile.age()).isEqualTo(30));
    }

    @Test
    @DisplayName("alias lookup is case-insensitive")
    void findsByAliasIgnoringCase() {
        String alias = uniqueAlias("Lookup");
        procedures.upsertProfile(newUser(), "Find", "Me", LocalDate.of(1990, 5, 5), alias, null);

        assertThat(profiles.findByAlias(alias.toLowerCase())).isPresent();
        assertThat(profiles.findByAlias(alias.toUpperCase())).isPresent();
    }

    @Test
    @DisplayName("batch lookup returns only the users that have a profile")
    void batchLookupSkipsMissing() {
        UUID present = newUser();
        UUID absent = newUser();
        procedures.upsertProfile(present, "Batch", "User", LocalDate.of(1991, 2, 2),
                uniqueAlias("batch"), null);

        var found = profiles.findAllByUserIds(java.util.List.of(present, absent));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getUserId()).isEqualTo(present);
    }
}
