package dev.social.profile.profiles.services;

import dev.social.profile.profiles.domain.Profile;
import dev.social.profile.profiles.dto.ProfileResponse;
import dev.social.profile.profiles.dto.UpsertProfileRequest;
import dev.social.profile.profiles.exceptions.ProfileErrorCode;
import dev.social.profile.profiles.mappers.ProfileMapper;
import dev.social.profile.profiles.repositories.ProfileProcedureRepository;
import dev.social.profile.profiles.repositories.ProfileRepository;
import dev.social.shared.error.ConflictException;
import dev.social.shared.error.NotFoundException;
import dev.social.shared.error.SqlErrorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profiles;
    private final ProfileProcedureRepository procedures;
    private final ProfileMapper mapper;

    public ProfileService(ProfileRepository profiles,
                          ProfileProcedureRepository procedures,
                          ProfileMapper mapper) {
        this.profiles = profiles;
        this.procedures = procedures;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProfileResponse findByUserId(UUID userId) {
        return profiles.findByUserId(userId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException(
                        ProfileErrorCode.PROFILE_NOT_FOUND,
                        "No profile exists for user %s. Create one with PUT /api/profiles/me."
                                .formatted(userId)));
    }

    @Transactional(readOnly = true)
    public ProfileResponse findByAlias(String alias) {
        return profiles.findByAlias(alias)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of(
                        ProfileErrorCode.PROFILE_NOT_FOUND, "Profile", alias));
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> findAllByUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return profiles.findAllByUserIds(userIds).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Creates or updates the caller's own profile.
     *
     * <p>{@code userId} comes from the JWT, never from the request body, so
     * there is no way to address someone else's row.
     *
     * @return the saved profile and whether it was newly created, so the
     *         controller can answer 201 or 200 correctly
     */
    @Transactional
    public UpsertOutcome upsertOwnProfile(UUID userId, UpsertProfileRequest request) {
        try {
            ProfileProcedureRepository.UpsertResult result = procedures.upsertProfile(
                    userId,
                    request.firstName(),
                    request.lastName(),
                    request.birthDate(),
                    request.alias(),
                    request.bio());
            Profile saved = profiles.findById(result.profileId())
                    .orElseThrow(() -> new IllegalStateException(
                            "sp_upsert_profile returned id %s but no row was found"
                                    .formatted(result.profileId())));

            log.info("{} profile for user {} (alias '{}')",
                    result.created() ? "Created" : "Updated", userId, saved.getAlias());

            return new UpsertOutcome(mapper.toResponse(saved), result.created());

        } catch (DataAccessException exception) {
            throw translateUpsertFailure(exception, request);
        }
    }

    private RuntimeException translateUpsertFailure(DataAccessException exception,
                                                    UpsertProfileRequest request) {
        return SqlErrorSupport.raisedBusinessCode(exception)
                .map(procedureCode -> {
                    ProfileErrorCode errorCode = ProfileErrorCode.fromProcedureCode(procedureCode);
                    String detail = errorCode == ProfileErrorCode.ALIAS_ALREADY_EXISTS
                            ? "The alias '%s' is already taken".formatted(request.alias())
                            : "The profile could not be saved";
                    return (RuntimeException) new ConflictException(errorCode, detail);
                })
                .orElse(exception);
    }

    public record UpsertOutcome(ProfileResponse profile, boolean created) {
    }
}
