package dev.social.profile.profiles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

/**
 * The person behind an account: the names, birth date and alias the brief asks
 * the profile screen to show.
 *
 * <p>{@code userId} is a logical foreign key to {@code authdb.users.id}. It is
 * not, and must not become, a real constraint - that would tie two independent
 * databases together at the storage layer and undo the service split. Integrity
 * is kept by only ever creating a profile for a subject taken from a verified
 * JWT.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "alias", nullable = false, unique = true, length = 50)
    private String alias;

    @Column(name = "bio", length = 280)
    private String bio;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Profile() {
        // required by JPA
    }

    public Profile(UUID id,
                   UUID userId,
                   String firstName,
                   String lastName,
                   LocalDate birthDate,
                   String alias,
                   String bio) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.firstName = trim(firstName);
        this.lastName = trim(lastName);
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate");
        this.alias = trim(alias);
        this.bio = bio == null ? null : bio.trim();
    }

    public static Profile create(UUID userId,
                                 String firstName,
                                 String lastName,
                                 LocalDate birthDate,
                                 String alias,
                                 String bio) {
        return new Profile(UUID.randomUUID(), userId, firstName, lastName, birthDate, alias, bio);
    }

    public String fullName() {
        return "%s %s".formatted(firstName, lastName);
    }

    /**
     * Age in completed years.
     *
     * <p>Computed on read rather than stored: an age column is wrong from the
     * moment it is written and there is no event to correct it. {@link Period}
     * handles leap years and month lengths, which naive day arithmetic does not.
     */
    public int age() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public void updateDetails(String firstName,
                              String lastName,
                              LocalDate birthDate,
                              String alias,
                              String bio) {
        this.firstName = trim(firstName);
        this.lastName = trim(lastName);
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate");
        this.alias = trim(alias);
        this.bio = bio == null ? null : bio.trim();
    }

    private static String trim(String value) {
        return Objects.requireNonNull(value, "value").trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getAlias() {
        return alias;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Profile profile && Objects.equals(id, profile.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Profile{id=%s, userId=%s, alias='%s'}".formatted(id, userId, alias);
    }
}
