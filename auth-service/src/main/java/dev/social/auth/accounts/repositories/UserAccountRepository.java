package dev.social.auth.accounts.repositories;

import dev.social.auth.accounts.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Plain CRUD over {@code users}. The procedure-backed operations live in
 * {@link AccountProcedureRepository} so this interface stays derived-query only.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * Lookup used by login.
     *
     * <p>Written as an explicit query with {@code lower(:username)} rather than
     * a derived {@code findByUsernameIgnoreCase}: the latter generates
     * {@code upper(username) = upper(?)}, which cannot use the index, and
     * usernames are already stored lowercase.
     */
    @Query("SELECT u FROM UserAccount u WHERE u.username = lower(:username)")
    Optional<UserAccount> findByUsername(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM UserAccount u WHERE u.username = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM UserAccount u WHERE u.email = lower(:email)")
    boolean existsByEmail(@Param("email") String email);
}
