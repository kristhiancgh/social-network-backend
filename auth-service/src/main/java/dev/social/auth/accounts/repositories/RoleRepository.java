package dev.social.auth.accounts.repositories;

import dev.social.auth.accounts.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Read-only in practice: the two rows are inserted by migration V1 and nothing
 * in the application creates roles.
 */
public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(String name);
}
