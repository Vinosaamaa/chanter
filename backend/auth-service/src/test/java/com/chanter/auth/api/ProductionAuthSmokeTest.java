package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.auth.application.AuthEmailTokenRepository;
import com.chanter.auth.application.AuthUserRepository;
import com.chanter.auth.domain.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "chanter.internal-service-token=test-internal-service-token-for-auth",
        "chanter.auth.require-email-verification=true",
        "chanter.auth.rate-limit.max-requests=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionAuthSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private AuthEmailTokenRepository emailTokenRepository;

    @Test
    void registerRequiresVerificationThenPasswordResetWorks() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@study.local";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123",
                                "displayName", "Verify Me"
                        ))))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        assertThat(registerBody.get("verificationRequired").asBoolean()).isTrue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"
                        ))))
                .andExpect(status().isForbidden());

        AuthUser user = authUserRepository.findByEmail(email).orElseThrow();
        String verifyToken = "verify-token-" + UUID.randomUUID();
        emailTokenRepository.save(
                UUID.randomUUID(),
                user.id(),
                sha256(verifyToken),
                "EMAIL_VERIFY",
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verifyToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        String resetToken = "reset-token-" + UUID.randomUUID();
        emailTokenRepository.save(
                UUID.randomUUID(),
                user.id(),
                sha256(resetToken),
                "PASSWORD_RESET",
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "password", "newpassword123"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "newpassword123"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void oauthProvidersEmptyWhenNotConfigured() throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/auth/oauth/providers"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("providers").isArray()).isTrue();
        assertThat(body.get("providers")).isEmpty();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
