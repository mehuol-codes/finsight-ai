package dev.mehuol.finsight.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import dev.mehuol.finsight.IntegrationTestSupport;
import dev.mehuol.finsight.service.ChatHistoryService;

/** Sign-up, sign-in, CSRF and per-user isolation of chats and documents. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTests extends IntegrationTestSupport {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AppUserDetailsService userDetails;
    @Autowired
    private ChatHistoryService history;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private AppUserDetails account(String email) {
        return (AppUserDetails) userDetails.loadUserByUsername(email);
    }

    @Test
    @Order(1)
    void firstAccountTakesOverChatsCreatedBeforeLogin() throws Exception {
        // Other test classes share this throwaway database; start from "no accounts yet".
        jdbc.update("DELETE FROM app_user");
        String legacyChat = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO chat (id, title) VALUES (?, 'Old chat')", legacyChat);

        register("owner@example.com");

        Long owner = jdbc.queryForObject("SELECT user_id FROM chat WHERE id = ?", Long.class, legacyChat);
        assertThat(owner).isEqualTo(account("owner@example.com").id());
    }

    @Test
    void apiNeedsASignedInUserButThePageDoesNot() throws Exception {
        mvc.perform(get("/api/chats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/gold/chart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    // Runs first: spring-security-test's csrf() permanently switches the shared CSRF filter to a
    // session-based token store, after which no cookie would be written in this context.
    @Test
    @Order(0)
    void responsesCarryTheCsrfCookieForThePage() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void rejectsInvalidAndDuplicateSignUps() throws Exception {
        String bad = "{\"email\":\"not-an-email\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
        String shortPassword = "{\"email\":\"short@example.com\",\"password\":\"abc\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(shortPassword)).andExpect(status().isBadRequest());

        register("dup@example.com");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"DUP@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void signUpWithoutCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nocsrf@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signsInWithEmailAndPassword() throws Exception {
        register("login@example.com");

        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", "login@example.com").param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", "login@example.com").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void passwordsAreStoredHashed() throws Exception {
        register("hash@example.com");
        String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class,
                "hash@example.com");
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void usersCannotSeeOrChangeEachOthersChats() throws Exception {
        register("alice@example.com");
        register("bob@example.com");
        AppUserDetails alice = account("alice@example.com");
        AppUserDetails bob = account("bob@example.com");
        String aliceChat = UUID.randomUUID().toString();
        history.ensureChat(aliceChat, "Alice's portfolio", alice.id());

        mvc.perform(get("/api/chats/" + aliceChat + "/messages").with(user(alice))).andExpect(status().isOk());
        mvc.perform(get("/api/chats").with(user(bob)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(aliceChat))));

        mvc.perform(get("/api/chats/" + aliceChat + "/messages").with(user(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents").param("conversationId", aliceChat).with(user(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/chats/" + aliceChat).with(user(bob)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/chats/" + aliceChat).with(user(bob)).with(csrf())).andExpect(status().isNotFound());

        String title = jdbc.queryForObject("SELECT title FROM chat WHERE id = ?", String.class, aliceChat);
        assertThat(title).isEqualTo("Alice's portfolio");
    }
}
