package dev.social.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.social.shared.security.AuthenticatedUser;
import dev.social.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the auth endpoints through the real filter chain: CORS, the JWT
 * filter, the security rules and the RFC 7807 handler all take part, which a
 * plain controller unit test would skip entirely.
 *
 * <p>Not {@code @Transactional}. {@code LoginAuditService} commits in its own
 * {@code REQUIRES_NEW} transaction, so a test-managed rollback would give a
 * false picture of what actually survives. Each test uses its own username
 * instead.
 */
@AutoConfigureMockMvc
class AuthenticationEndpointIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private static final String PASSWORD = "Password123!";

    // -------------------------------------------------------------------------
    //  Registration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register creates an account and returns 201 with a Location header")
    void registersAccount() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("reguser", "reguser@social.dev", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.username").value("reguser"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register rejects a duplicate username with the full problem contract")
    void rejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupuser", "dupuser@social.dev", PASSWORD)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupuser", "other@social.dev", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.type").value("https://social.dev/errors/username-already-exists"))
                .andExpect(jsonPath("$.title").value("Username already taken"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"))
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register reports every invalid field, sorted, as 422")
    void reportsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("password"))
                .andExpect(jsonPath("$.errors[2].field").value("password"))
                .andExpect(jsonPath("$.errors[3].field").value("username"))
                .andExpect(jsonPath("$.detail").value("4 fields failed validation"));
    }

    @Test
    @DisplayName("Malformed JSON is a 400 that does not echo the payload back")
    void rejectsMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("this is not json");
    }

    // -------------------------------------------------------------------------
    //  Login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/login returns a JWT whose claims match the account")
    void loginIssuesUsableToken() throws Exception {
        register("loginuser", "loginuser@social.dev");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"loginuser","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.username").value("loginuser"))
                .andReturn();

        String token = json(result).get("accessToken").asText();

        AuthenticatedUser parsed = tokenProvider.parse(token);
        assertThat(parsed.username()).isEqualTo("loginuser");
        assertThat(parsed.roles()).containsExactly("ROLE_USER");
        assertThat(parsed.userId()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/auth/login works too, and forbids caching the response")
    void deprecatedGetLoginStillWorks() throws Exception {
        register("getuser", "getuser@social.dev");

        mockMvc.perform(get("/api/auth/login")
                        .param("username", "getuser")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate"));
    }

    @Test
    @DisplayName("A wrong password and an unknown user return the identical error")
    void doesNotRevealWhetherAccountExists() throws Exception {
        register("realuser", "realuser@social.dev");

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"realuser","password":"WrongPassword9"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String noSuchUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobodyhere","password":"WrongPassword9"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(field(wrongPassword, "errorCode")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(field(noSuchUser, "errorCode")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(field(wrongPassword, "detail")).isEqualTo(field(noSuchUser, "detail"));
    }

    @Test
    @DisplayName("The fifth wrong password locks the account, and the right one no longer helps")
    void locksAccountAfterRepeatedFailures() throws Exception {
        register("lockme", "lockme@social.dev");

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"lockme","password":"WrongPassword9"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lockme","password":"WrongPassword9"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lockme","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    // -------------------------------------------------------------------------
    //  Token handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/auth/me echoes the claims of a valid token")
    void meReturnsTokenIdentity() throws Exception {
        register("meuser", "meuser@social.dev");
        String token = login("meuser");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meuser"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    @DisplayName("A protected endpoint without a token is 401 UNAUTHENTICATED")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("A tampered token is 401 INVALID_TOKEN, not a 500")
    void rejectsTamperedToken() throws Exception {
        register("tamper", "tamper@social.dev");
        String token = login("tamper");

        String[] segments = token.split("\\.");
        char first = segments[1].charAt(0);
        segments[1] = (first == 'e' ? 'f' : 'e') + segments[1].substring(1);
        String forged = String.join(".", segments);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("A token signed with a different secret is rejected")
    void rejectsForeignlySignedToken() throws Exception {
        var foreignProvider = new JwtTokenProvider(new dev.social.shared.security.JwtProperties(
                "a-completely-different-secret-key-of-sufficient-length-42",
                "social-network",
                java.time.Duration.ofHours(1),
                java.time.Duration.ofSeconds(30)));

        String foreignToken = foreignProvider
                .issue(UUID.randomUUID(), "intruder", java.util.List.of("ROLE_ADMIN"))
                .token();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Every response carries an X-Trace-Id, and an inbound one is preserved")
    void propagatesTraceId() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(header().exists("X-Trace-Id"));

        mockMvc.perform(get("/api/auth/me").header("X-Trace-Id", "my-trace-123"))
                .andExpect(header().string("X-Trace-Id", "my-trace-123"))
                .andExpect(jsonPath("$.traceId").value("my-trace-123"));
    }

    @Test
    @DisplayName("An unknown path answers with the problem contract, not Spring's default")
    void unknownPathUsesProblemContract() throws Exception {
        mockMvc.perform(get("/api/auth/does-not-exist")
                        .header("Authorization", "Bearer " + bootstrapToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private String body(String username, String email, String password) {
        return """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private String bootstrapToken() throws Exception {
        register("pathuser", "pathuser@social.dev");
        return login("pathuser");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String field(String payload, String name) throws Exception {
        return objectMapper.readTree(payload).get(name).asText();
    }
}
