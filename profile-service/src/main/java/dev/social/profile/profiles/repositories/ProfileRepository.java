package dev.social.profile.profiles.repositories;

import dev.social.profile.profiles.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);

    @Query("SELECT p FROM Profile p WHERE LOWER(p.alias) = LOWER(:alias)")
    Optional<Profile> findByAlias(@Param("alias") String alias);

    boolean existsByUserId(UUID userId);

    @Query("SELECT p FROM Profile p WHERE p.userId IN :userIds")
    List<Profile> findAllByUserIds(@Param("userIds") List<UUID> userIds);
}
