package com.chanter.community.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Smoke tests for SEC-01: service-level identity enforcement on public API routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticatedUserFilterSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-community";
    private static final String WRONG_TOKEN = "wrong-service-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void headerOnlyWithoutServiceTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/study-servers")
                        .header(AuthHeaders.USER_ID, "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceTokenPlusUserIdIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/study-servers")
                        .header(AuthHeaders.USER_ID, "00000000-0000-0000-0000-000000000001")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void validJwtAloneIsAccepted() throws Exception {
        String token = jwtTokenService.createAccessToken(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        mockMvc.perform(get("/api/v1/study-servers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void spoofedUserIdHeaderWithValidJwtMustMatch() throws Exception {
        java.util.UUID realUserId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000003");
        java.util.UUID spoofedUserId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000099");
        String token = jwtTokenService.createAccessToken(realUserId);

        mockMvc.perform(get("/api/v1/study-servers")
                        .header("Authorization", "Bearer " + token)
                        .header(AuthHeaders.USER_ID, spoofedUserId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongServiceTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/study-servers")
                        .header(AuthHeaders.USER_ID, "00000000-0000-0000-0000-000000000001")
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, WRONG_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noAuthIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/study-servers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalPathIsNotFiltered() throws Exception {
        mockMvc.perform(get("/api/v1/internal/some-endpoint"))
                .andExpect(status().is4xxClientError());
    }
}
