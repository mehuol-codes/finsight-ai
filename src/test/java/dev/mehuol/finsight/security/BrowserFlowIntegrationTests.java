package dev.mehuol.finsight.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import dev.mehuol.finsight.IntegrationTestSupport;

/**
 * Drives the real HTTP server the way index.html does: read the XSRF-TOKEN cookie, send it back
 * in the X-XSRF-TOKEN header, keep the session cookie between calls.
 */
class BrowserFlowIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private Environment environment;

    private HttpClient browser;
    private CookieManager cookies;
    private String base;

    @BeforeEach
    void newBrowser() {
        cookies = new CookieManager();
        browser = HttpClient.newBuilder().cookieHandler(cookies).build();
        base = "http://localhost:" + environment.getProperty("local.server.port");
    }

    private String csrfCookie() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst().orElse("");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String contentType, String body, boolean withCsrf)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (withCsrf) {
            request.header("X-XSRF-TOKEN", csrfCookie());
        }
        return browser.send(request.build(), BodyHandlers.ofString());
    }

    @Test
    void signUpSignInUseTheAppAndSignOut() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        String json = "{\"email\":\"" + email + "\",\"password\":\"flow-password-1\"}";

        // First load: not signed in, but the page receives a CSRF cookie.
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(csrfCookie()).isNotBlank();

        // State-changing calls without the header are refused.
        assertThat(send("POST", "/api/auth/register", "application/json", json, false).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/auth/register", "application/json", json, true).statusCode()).isEqualTo(201);

        HttpResponse<String> login = send("POST", "/api/auth/login", "application/x-www-form-urlencoded",
                "email=" + email + "&password=flow-password-1", true);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains(email);

        // Signing in rotates the CSRF token; /me hands out the new one, as the page does.
        HttpResponse<String> me = get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email);

        assertThat(get("/api/chats").statusCode()).isEqualTo(200);
        // A protected write with the rotated token gets past CSRF (404: the chat doesn't exist).
        assertThat(send("PATCH", "/api/chats/" + UUID.randomUUID(), "application/json", "{\"title\":\"x\"}", true)
                .statusCode()).isEqualTo(404);

        assertThat(send("POST", "/api/auth/logout", null, null, true).statusCode()).isEqualTo(204);
        assertThat(get("/api/chats").statusCode()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsRejectedWithJson() throws Exception {
        get("/api/auth/me");
        HttpResponse<String> login = send("POST", "/api/auth/login", "application/x-www-form-urlencoded",
                "email=nobody@example.com&password=whatever-123", true);
        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body()).contains("Wrong email or password");
    }
}
