package dev.social.profile.profiles.mappers;

import dev.social.profile.profiles.domain.Profile;
import dev.social.profile.profiles.dto.ProfileResponse;
import org.springframework.stereotype.Component;

/** Entity to DTO. Hand-written for the same reasons as in auth-service. */
@Component
public class ProfileMapper {

    public ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.fullName(),
                profile.getBirthDate(),
                profile.age(),
                profile.getAlias(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getCreatedAt());
    }
}
