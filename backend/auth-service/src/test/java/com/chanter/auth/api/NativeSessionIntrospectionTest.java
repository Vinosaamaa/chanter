package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chanter.common.auth.JwtTokenService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "chanter.auth.rate-limit.max-requests=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NativeSessionIntrospectionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    private static final String INTERNAL = "/internal/v1/auth/session/introspect";
    private static final String SERVICE = "test-internal-service-token-for-auth";

    @Test
    void rotatedTokensKeepOneSessionAndLogoutRevokesBothAccessTokensImmediately() throws Exception {
        var first = register();
        String original = bearer(first);
        var identity = introspect(original, 200);
        var rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first.getResponse().getCookie("chanter_refresh"))
                .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(introspect(bearer(rotated), 200).getResponse().getContentAsString()).get("sessionId"))
                .isEqualTo(json.readTree(identity.getResponse().getContentAsString()).get("sessionId"));
        mvc.perform(post("/api/v1/auth/logout").cookie(first.getResponse().getCookie("chanter_refresh"))
                .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isNoContent());
        introspect(original, 401);
        introspect(bearer(rotated), 401);
    }

    @Test
    void refreshReplayRevokesNativeAuthorityAndInternalEndpointRejectsMissingServiceAuthentication() throws Exception {
        var first = register();
        mvc.perform(post(INTERNAL).header("Authorization", bearer(first))).andExpect(status().isUnauthorized());
        Cookie original = first.getResponse().getCookie("chanter_refresh");
        var rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(original)
                .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isOk()).andReturn();
        introspect(bearer(rotated), 200);
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)
                .header("Origin", "http://localhost:5173").header("X-Chanter-CSRF", "1"))
                .andExpect(status().isUnauthorized());
        introspect(bearer(rotated), 401);
        introspect(bearer(first), 401);
    }

    @Test
    void legacyTokensAndExpiredOrForeignSessionsCannotBecomeNativeAuthority() throws Exception {
        var first = register();
        UUID user = UUID.fromString(json.readTree(first.getResponse().getContentAsString()).path("user").path("id").asText());
        var active = json.readTree(introspect(bearer(first), 200).getResponse().getContentAsString());
        UUID session = UUID.fromString(active.path("sessionId").asText());
        String legacy = "Bearer " + tokens.createAccessToken(user);
        assertThat(tokens.parseUserId(legacy)).isEqualTo(user);
        introspect(legacy, 401);
        introspect("Bearer " + tokens.createAccessToken(UUID.randomUUID(), session), 401);
        introspect("Bearer " + tokens.createAccessToken(user, UUID.randomUUID()), 401);
        jdbc.update("UPDATE auth_sessions SET expires_at=? WHERE id=?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), session);
        introspect(bearer(first), 401);
    }

    private MvcResult register() throws Exception {
        return mvc.perform(post("/api/v1/auth/register").header("Origin", "http://localhost:5173")
                .header("X-Chanter-CSRF", "1").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.invalid",
                        "password", "test-password-123", "displayName", "Native fixture"))))
                .andExpect(status().isCreated()).andReturn();
    }
    private String bearer(MvcResult result) throws Exception {
        return "Bearer " + json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
    private MvcResult introspect(String bearer, int expected) throws Exception {
        return mvc.perform(post(INTERNAL).header("Authorization", bearer)
                .header("X-Chanter-Internal-Service-Token", SERVICE)).andExpect(status().is(expected)).andReturn();
    }
}
