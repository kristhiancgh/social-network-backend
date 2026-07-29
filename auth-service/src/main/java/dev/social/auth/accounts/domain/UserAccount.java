package dev.social.auth.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A set of credentials. Named {@code UserAccount} rather than {@code User}
 * because it is not the person - the person (names, birth date, alias) lives in
 * profile-service. This table only answers "can you prove you are this
 * subject?".
 *
 * <p>The id is assigned in Java, not by the database, even though the column
 * carries a {@code DEFAULT gen_random_uuid()}. Two reasons: the seeders across
 * four independent databases have to agree on the same user ids, and code that
 * creates a user gets the id without waiting for a flush.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** BCrypt digest. The plain password is never held in a field. */
    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * EAGER on purpose, and the only association on this entity. Authorities are
     * needed on every single login to build the JWT claims, so lazy loading
     * would mean a guaranteed second query plus a detached-proxy trap once the
     * entity leaves the transaction.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /** Maintained by the trg_users_touch_updated_at trigger, never by Java. */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected UserAccount() {
        // required by JPA
    }

    public UserAccount(UUID id, String username, String email, String passwordHash) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = normalise(username);
        this.email = normalise(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.enabled = true;
    }

    /** Creates an account with a fresh random id. */
    public static UserAccount create(String username, String email, String passwordHash) {
        return new UserAccount(UUID.randomUUID(), username, email, passwordHash);
    }

    public void grant(Role role) {
        roles.add(role);
    }

    /** Authority names for the JWT {@code roles} claim, in a stable order. */
    public List<String> roleNames() {
        return roles.stream().map(Role::getName).sorted().toList();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
    }

    public void disable() {
        this.enabled = false;
    }

    private static String normalise(String value) {
        // The DB enforces lowercase via ck_users_username_fmt; normalising here
        // turns a would-be 500 from a constraint into simply working.
        return Objects.requireNonNull(value, "value").trim().toLowerCase();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
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
        return other instanceof UserAccount account && Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** Never includes the hash - this string ends up in logs. */
    @Override
    public String toString() {
        return "UserAccount{id=%s, username='%s', enabled=%s}".formatted(id, username, enabled);
    }
}
