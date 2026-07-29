package dev.social.auth.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * An authority a user can hold. Rows are fixed by migration V1
 * ({@code ROLE_USER}, {@code ROLE_ADMIN}) and never created at runtime.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "name", nullable = false, unique = true, length = 30)
    private String name;

    /** Ids as seeded by V1__auth_schema.sql. */
    public static final short USER_ROLE_ID = 1;
    public static final short ADMIN_ROLE_ID = 2;

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    protected Role() {
        // required by JPA
    }

    public Role(Short id, String name) {
        this.id = id;
        this.name = name;
    }

    public Short getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Roles are compared by id, never by identity: two loads of ROLE_USER
        // from different sessions must be equal or Set<Role> breaks.
        return other instanceof Role role && Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
