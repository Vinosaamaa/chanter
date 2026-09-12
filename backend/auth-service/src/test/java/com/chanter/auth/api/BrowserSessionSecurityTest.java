package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import com.chanter.auth.application.AuthEmailTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "chanter.auth.rate-limit.max-requests=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrowserSessionSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthEmailTokenRepository emailTokens;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentRefreshAllowsOneRotationThenRevokesTheFamilyOnReplay() throws Exception {
        var registered = register();
        Cookie original = registered.getResponse().getCookie("chanter_refresh");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> attempt = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(browserPost("/refresh").cookie(original)).andReturn();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(responses.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
            var winner = responses.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst().orElseThrow();
            mvc.perform(browserPost("/refresh").cookie(winner.getResponse().getCookie("chanter_refresh")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void passwordResetRevokesEveryExistingBrowserSession() throws Exception {
        var registered = register();
        var body = json.readTree(registered.getResponse().getContentAsString());
        UUID userId = UUID.fromString(body.get("user").get("id").asText());
        var second = mvc.perform(browserPost("/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", body.get("user").get("email").asText(),
                                "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        String reset = UUID.randomUUID().toString();
        emailTokens.save(UUID.randomUUID(), userId,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reset.getBytes(StandardCharsets.UTF_8))),
                "PASSWORD_RESET", Instant.now().plusSeconds(60));
        mvc.perform(browserPost("/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", reset, "password", "changed-password123"))))
                .andExpect(status().isOk());
        for (var session : List.of(registered, second)) {
            mvc.perform(browserPost("/refresh").cookie(session.getResponse().getCookie("chanter_refresh")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessions").isEmpty());
    }

    @Test
    void expiredSessionAndJsonCredentialsCannotRestoreAuthentication() throws Exception {
        var registered = register();
        var body = json.readTree(registered.getResponse().getContentAsString());
        Cookie cookie = registered.getResponse().getCookie("chanter_refresh");
        mvc.perform(browserPost("/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", cookie.getValue()))))
                .andExpect(status().isNoContent());
        jdbc.update("UPDATE auth_sessions SET expires_at = ? WHERE user_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), UUID.fromString(body.get("user").get("id").asText()));
        mvc.perform(browserPost("/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesEntireSessionAndCurrentDeviceRevocationClearsItsCookie() throws Exception {
        var registered = register();
        Cookie original = registered.getResponse().getCookie("chanter_refresh");
        var rotated = mvc.perform(browserPost("/refresh").cookie(original)).andExpect(status().isOk()).andReturn();
        var logout = mvc.perform(browserPost("/logout").cookie(original)).andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0", "Secure", "HttpOnly");
        mvc.perform(browserPost("/refresh").cookie(rotated.getResponse().getCookie("chanter_refresh")))
                .andExpect(status().isUnauthorized());
        var fresh = register();
        String bearer = "Bearer " + json.readTree(fresh.getResponse().getContentAsString()).get("accessToken").asText();
        Cookie current = fresh.getResponse().getCookie("chanter_refresh");
        var listed = mvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer).cookie(current)).andReturn();
        String id = json.readTree(listed.getResponse().getContentAsString()).get("sessions").get(0).get("id").asText();
        var revoked = mvc.perform(delete("/api/v1/auth/sessions/" + id).header("Authorization", bearer).cookie(current)
                        .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(revoked.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(browserPost("/refresh").cookie(current)).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanListStableSessionsAndRevokeAnotherDeviceButCannotRevokeAnotherAccount() throws Exception {
        var first = register();
        var body = json.readTree(first.getResponse().getContentAsString());
        String bearer = "Bearer " + body.get("accessToken").asText();
        Cookie firstCookie = first.getResponse().getCookie("chanter_refresh");
        var second = mvc.perform(browserPost("/login").header("User-Agent", "Second browser")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "email", body.get("user").get("email").asText(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        Cookie secondCookie = second.getResponse().getCookie("chanter_refresh");
        mvc.perform(get("/api/v1/auth/sessions").cookie(firstCookie)).andExpect(status().isUnauthorized());
        var listed = mvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer).cookie(firstCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessions.length()").value(2)).andReturn();
        var sessions = json.readTree(listed.getResponse().getContentAsString()).get("sessions");
        var current = sessions.get(0).get("current").asBoolean() ? sessions.get(0) : sessions.get(1);
        var other = sessions.get(0).get("current").asBoolean() ? sessions.get(1) : sessions.get(0);
        assertThat(current.get("userAgent").asText()).isEqualTo("Test browser");
        assertThat(other.get("userAgent").asText()).isEqualTo("Second browser");
        String currentId = current.get("id").asText();
        var rotated = mvc.perform(browserPost("/refresh").cookie(firstCookie)).andExpect(status().isOk()).andReturn();
        Cookie rotatedCookie = rotated.getResponse().getCookie("chanter_refresh");
        var after = mvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer).cookie(rotatedCookie))
                .andExpect(status().isOk()).andReturn();
        assertThat(after.getResponse().getContentAsString()).contains(currentId, current.get("expiresAt").asText())
                .doesNotContain("tokenHash", "refreshToken");
        var stranger = register();
        String strangerBearer = "Bearer " + json.readTree(stranger.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(delete("/api/v1/auth/sessions/" + other.get("id").asText()).header("Authorization", strangerBearer)
                        .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/auth/sessions/" + other.get("id").asText()).header("Authorization", bearer)
                        .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isNoContent());
        mvc.perform(browserPost("/refresh").cookie(secondCookie)).andExpect(status().isUnauthorized());
        mvc.perform(browserPost("/refresh").cookie(rotatedCookie)).andExpect(status().isOk());
    }

    @Test
    void replayRevokesTheReplacementAndKeepsRevocationAfterTheUnauthorizedResponse() throws Exception {
        var registered = register();
        Cookie original = registered.getResponse().getCookie("chanter_refresh");
        var rotated = mvc.perform(browserPost("/refresh").cookie(original))
                .andExpect(status().isOk()).andReturn();
        Cookie replacement = rotated.getResponse().getCookie("chanter_refresh");
        assertThat(replacement.getValue()).isNotEqualTo(original.getValue());
        mvc.perform(browserPost("/refresh").cookie(original)).andExpect(status().isUnauthorized());
        // Separate HTTP request/transaction: the replay revocation must have committed despite the 401.
        var rejected = mvc.perform(browserPost("/refresh").cookie(replacement))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(rejected.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    private MvcResult register() throws Exception {
        return mvc.perform(browserPost("/register").header("User-Agent", "Test browser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@study.local",
                                "password", "password123", "displayName", "Browser"))))
                .andExpect(status().isCreated()).andReturn();
    }

    private static MockHttpServletRequestBuilder browserPost(String suffix) {
        return post("/api/v1/auth" + suffix).header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1");
    }

    @Test
    void cookieEndpointsRejectMissingOrForeignOriginAndMissingCsrfHeader() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
        for (String origin : new String[] {"null", "https://attacker.example", "http://localhost:5173.attacker.example"}) {
            mvc.perform(post("/api/v1/auth/logout").header("Origin", origin).header("X-Chanter-CSRF", "1"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:5173")
                        .header("X-Chanter-CSRF", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void registrationKeepsRenewableCredentialOnlyInSecureHttpOnlyCookie() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/register")
                        .header("Origin", "http://localhost:5173")
                        .header("X-Chanter-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@study.local",
                                "password", "password123", "displayName", "Browser"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("chanter_refresh=", "Secure", "HttpOnly", "SameSite=Strict", "Path=/api/v1/auth")
                .doesNotContain("Domain=");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }
}
