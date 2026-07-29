package dev.social.post.integration;

import dev.social.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The post endpoints end to end.
 *
 * <p>profile-service is not running here, so the alias lookup fails on every
 * publish - which is exactly the degradation the client was built for, and this
 * suite proves posts are still published without it. The timeout is dropped to
 * 100 ms so the refused connection does not slow the suite down.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.clients.profile-service.timeout-ms=100")
class PostEndpointIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private UUID userId;
    private String bearer;

    @BeforeEach
    void issueToken() {
        userId = UUID.randomUUID();
        bearer = "Bearer " + tokenProvider.issue(userId, "tester", List.of("ROLE_USER")).token();
    }

    private String body(String message) {
        return """
                {"message":"%s"}
                """.formatted(message);
    }

    private String uniqueMessage(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/posts publishes, with the author from the token and the date from the server")
    void publishes() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueMessage("Hello"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andExpect(jsonPath("$.authorUsername").value("tester"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
    }

    @Test
    @DisplayName("publishing still works when profile-service cannot be reached")
    void publishesWithoutProfileService() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueMessage("No profile service"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorAlias").doesNotExist());
    }

    @Test
    @DisplayName("an empty message is 422, not a 500 from the database")
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("message"));
    }

    @Test
    @DisplayName("a message over 500 characters is 422")
    void rejectsOverlongMessage() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x".repeat(501))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("message"));
    }

    @Test
    @DisplayName("the same message twice within 30 seconds is 409 DUPLICATE_POST")
    void rejectsDoubleSubmit() throws Exception {
        String message = uniqueMessage("Double submit");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(message)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(message)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_POST"));
    }

    @Test
    @DisplayName("GET /api/posts leaves out the caller's own posts by default")
    void timelineExcludesOwnPosts() throws Exception {
        String mine = uniqueMessage("Mine");
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mine)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/posts").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(mine);
    }

    @Test
    @DisplayName("includeOwn=true brings the caller's own posts back")
    void timelineCanIncludeOwnPosts() throws Exception {
        String mine = uniqueMessage("Mine too");
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mine)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/posts")
                        .header("Authorization", bearer)
                        .param("includeOwn", "true"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(mine);
    }

    @Test
    @DisplayName("the page payload is our own shape, not Spring Data's")
    void pageShapeIsStable() throws Exception {
        mockMvc.perform(get("/api/posts").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.last").exists())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    @DisplayName("an out-of-range page size is 422 rather than letting a client read the table")
    void capsPageSize() throws Exception {
        mockMvc.perform(get("/api/posts")
                        .header("Authorization", bearer)
                        .param("size", "5000"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("the author can delete their own post, and it disappears from reads")
    void ownerCanDelete() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueMessage("Temporary"))))
                .andExpect(status().isCreated())
                .andReturn();

        String postId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("deleting somebody else's post is 403 NOT_POST_OWNER")
    void nonOwnerCannotDelete() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueMessage("Protected"))))
                .andExpect(status().isCreated())
                .andReturn();

        String postId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        String intruderBearer = "Bearer " + tokenProvider
                .issue(UUID.randomUUID(), "intruder", List.of("ROLE_USER")).token();

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", intruderBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_POST_OWNER"));
    }

    @Test
    @DisplayName("reading the timeline requires a token")
    void timelineRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("the OpenAPI document is published")
    void publishesOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Posts API"));
    }
}
