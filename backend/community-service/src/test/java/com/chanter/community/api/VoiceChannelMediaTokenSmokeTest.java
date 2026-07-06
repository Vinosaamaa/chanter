package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoiceChannelMediaTokenSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studyServerMemberReceivesLiveKitMediaTokenForVoiceChannel() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID nonMemberUserId = UUID.randomUUID();

        ChannelResponse voiceChannel = createStudyServer(ownerUserId).channels().stream()
                .filter(channel -> channel.kind().equals("VOICE"))
                .findFirst()
                .orElseThrow();

        MvcResult tokenResult = mockMvc.perform(post(
                        "/api/v1/study-server-channels/{channelId}/media-token",
                        voiceChannel.id()
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();

        VoiceMediaTokenResponse token = objectMapper.readValue(
                tokenResult.getResponse().getContentAsString(),
                VoiceMediaTokenResponse.class
        );

        assertThat(token.roomName()).isEqualTo("voice-" + voiceChannel.id());
        assertThat(token.serverUrl()).isEqualTo("ws://localhost:7880");
        assertThat(token.participantToken()).isNotBlank();
        assertThat(token.canSpeak()).isTrue();
        assertThat(token.canListen()).isTrue();

        mockMvc.perform(post(
                        "/api/v1/study-server-channels/{channelId}/media-token",
                        voiceChannel.id()
                ).with(asUser(nonMemberUserId)))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Voice Media Study Group"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private record StudyServerResponse(List<ChannelResponse> channels) {
    }

    private record ChannelResponse(UUID id, String name, String kind) {
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
