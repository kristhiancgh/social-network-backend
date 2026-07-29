package dev.social.profile.integration;

import dev.social.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProfileEndpointIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private UUID userId;
    private String bearer;
    private String alias;

    @BeforeEach
    void issueToken() {
        userId = UUID.randomUUID();
        bearer = "Bearer " + tokenProvider.issue(userId, "tester", List.of("ROLE_USER")).token();
        alias = "u" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
    }

    private String body(String firstName, String lastName, String birthDate, String theAlias) {
        return """
                {"firstName":"%s","lastName":"%s","birthDate":"%s","alias":"%s","bio":"Hello"}
                """.formatted(firstName, lastName, birthDate, theAlias);
    }

    @Test
    @DisplayName("GET /api/profiles/me is 404 PROFILE_NOT_FOUND before the profile is filled in")
    void missingProfileIsNotAnError() throws Exception {
        mockMvc.perform(get("/api/profiles/me").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.service").value("profile-service"));
    }

    @Test
    @DisplayName("PUT /api/profiles/me creates with 201 and a Location header")
    void createsProfile() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("John", "Doe", "1992-03-14", alias)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.birthDate").value("1992-03-14"))
                .andExpect(jsonPath("$.alias").value(alias))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.age").isNumber())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("PUT /api/profiles/me updates with 200 on the second call")
    void updatesProfile() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("John", "Doe", "1992-03-14", alias)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Johnathan", "Doe", "1992-03-14", alias)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Johnathan"));
    }

    @Test
    @DisplayName("GET /api/profiles/me returns the saved profile")
    void readsOwnProfile() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Maria", "Garcia", "1988-11-02", alias)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/profiles/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Garcia"))
                .andExpect(jsonPath("$.alias").value(alias));
    }

    @Test
    @DisplayName("a taken alias is 409 ALIAS_ALREADY_EXISTS")
    void rejectsTakenAlias() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("First", "Owner", "1990-01-01", alias)))
                .andExpect(status().isCreated());

        String otherBearer = "Bearer " + tokenProvider
                .issue(UUID.randomUUID(), "other", List.of("ROLE_USER")).token();

        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", "Person", "1990-01-01", alias)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALIAS_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("a future birth date is rejected as 422 before it reaches the database")
    void rejectsFutureBirthDate() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Time", "Traveller", "2099-01-01", alias)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));
    }

    @Test
    @DisplayName("an alias with illegal characters is rejected as 422")
    void rejectsBadAlias() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("John", "Doe", "1992-03-14", "not valid!")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("alias"));
    }

    @Test
    @DisplayName("profiles are readable by any authenticated user")
    void profilesArePublicToMembers() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Public", "Person", "1990-06-06", alias)))
                .andExpect(status().isCreated());

        String otherBearer = "Bearer " + tokenProvider
                .issue(UUID.randomUUID(), "nosy", List.of("ROLE_USER")).token();

        mockMvc.perform(get("/api/profiles/{userId}", userId).header("Authorization", otherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value(alias));

        mockMvc.perform(get("/api/profiles/by-alias/{alias}", alias).header("Authorization", otherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("nothing is readable without a token - this service holds personal data")
    void everythingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/profiles/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the batch endpoint returns only the profiles that exist")
    void batchLookup() throws Exception {
        mockMvc.perform(put("/api/profiles/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Batch", "User", "1991-02-02", alias)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", bearer)
                        .param("userIds", userId + "," + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("the OpenAPI document is published")
    void publishesOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Profiles API"));
    }
}
