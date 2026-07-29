package dev.social.like.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The like endpoints through the real filter chain, so the JWT filter, the
 * security rules and the RFC 7807 handler all take part.
 */
@AutoConfigureMockMvc
class LikeEndpointIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private String bearer;

    @BeforeEach
    void issueToken() {
        userId = UUID.randomUUID();
        bearer = "Bearer " + tokenProvider
                .issue(userId, "tester", List.of("ROLE_USER"))
                .token();
    }

    private String toggleBody(UUID postId) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("postId", postId.toString()));
    }

    @Test
    @DisplayName("POST /api/likes toggles and returns the new state")
    void togglesLike() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleBody(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId.toString()))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleBody(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    @DisplayName("POST /api/likes without a token is 401 with the problem contract")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleBody(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.service").value("like-service"));
    }

    @Test
    @DisplayName("POST /api/likes rejects a missing postId as 422")
    void validatesBody() throws Exception {
        mockMvc.perform(post("/api/likes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("postId"));
    }

    @Test
    @DisplayName("GET /api/likes/counts answers for every requested post, zero included")
    void batchCounts() throws Exception {
        UUID liked = UUID.randomUUID();
        UUID untouched = UUID.randomUUID();

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleBody(liked)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/likes/counts")
                        .header("Authorization", bearer)
                        .param("postIds", liked + "," + untouched))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].likeCount").value(1))
                .andExpect(jsonPath("$[0].likedByMe").value(true))
                .andExpect(jsonPath("$[1].likeCount").value(0))
                .andExpect(jsonPath("$[1].likedByMe").value(false));
    }

    @Test
    @DisplayName("likedByMe is per user: another token sees the same total, different state")
    void likedByMeIsPerUser() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleBody(postId)))
                .andExpect(status().isOk());

        String otherBearer = "Bearer " + tokenProvider
                .issue(UUID.randomUUID(), "other", List.of("ROLE_USER"))
                .token();

        mockMvc.perform(get("/api/likes/post/{postId}", postId)
                        .header("Authorization", otherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    @DisplayName("GET /api/likes/post/{id}/users lists who liked it")
    void listsLikers() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/likes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleBody(postId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/likes/post/{postId}/users", postId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("tester"));
    }

    @Test
    @DisplayName("the counter rebuild endpoint is refused to a non-admin")
    void rebuildRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/likes/maintenance/rebuild-counters")
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("the counter rebuild endpoint works for an admin")
    void rebuildAllowedForAdmin() throws Exception {
        String adminBearer = "Bearer " + tokenProvider
                .issue(UUID.randomUUID(), "admin", List.of("ROLE_USER", "ROLE_ADMIN"))
                .token();

        mockMvc.perform(post("/api/likes/maintenance/rebuild-counters")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unparseable post id is a 400, not a 500")
    void rejectsMalformedUuid() throws Exception {
        mockMvc.perform(get("/api/likes/post/{postId}", "not-a-uuid")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("/docs is reachable without a token, so the API can be read")
    void docsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Likes API"));
    }
}
