package com.chanter.community.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalDmCallMediaTokenSmokeTest {

    private static final String INTERNAL_TOKEN = "test-internal-service-token-for-community";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void missingServiceTokenIsUnauthorized() throws Exception {
        UUID callId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID callee = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/dm-calls/{callId}/media-token", callId)
                        .header(AuthHeaders.USER_ID, caller.toString())
                        .header(InternalDmCallController.CALLER_USER_ID_HEADER, caller.toString())
                        .header(InternalDmCallController.CALLEE_USER_ID_HEADER, callee.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongServiceTokenIsUnauthorized() throws Exception {
        UUID callId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID callee = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/dm-calls/{callId}/media-token", callId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "wrong-token")
                        .header(AuthHeaders.USER_ID, caller.toString())
                        .header(InternalDmCallController.CALLER_USER_ID_HEADER, caller.toString())
                        .header(InternalDmCallController.CALLEE_USER_ID_HEADER, callee.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonParticipantIsForbidden() throws Exception {
        UUID callId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID callee = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/dm-calls/{callId}/media-token", callId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .header(AuthHeaders.USER_ID, outsider.toString())
                        .header(InternalDmCallController.CALLER_USER_ID_HEADER, caller.toString())
                        .header(InternalDmCallController.CALLEE_USER_ID_HEADER, callee.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void participantWithServiceTokenReceivesLiveKitToken() throws Exception {
        UUID callId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        UUID callee = UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/internal/v1/dm-calls/{callId}/media-token", callId)
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_TOKEN)
                        .header(AuthHeaders.USER_ID, callee.toString())
                        .header(InternalDmCallController.CALLER_USER_ID_HEADER, caller.toString())
                        .header(InternalDmCallController.CALLEE_USER_ID_HEADER, callee.toString()))
                .andExpect(status().isOk())
                .andReturn();

        VoiceMediaTokenResponse token = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                VoiceMediaTokenResponse.class
        );

        assertThat(token.roomName()).isEqualTo("dm-call-" + callId);
        assertThat(token.serverUrl()).isEqualTo("ws://localhost:7880");
        assertThat(token.participantToken()).isNotBlank();
        assertThat(token.canSpeak()).isTrue();
        assertThat(token.canListen()).isTrue();
    }

    private record VoiceMediaTokenResponse(
            String roomName,
            String serverUrl,
            String participantToken,
            boolean canSpeak,
            boolean canListen
    ) {
    }
}
