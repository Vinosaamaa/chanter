package com.chanter.community.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class VoiceChannelPresenceSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studyServerMemberCanJoinAndLeaveVoiceChannelWithVisiblePresence() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID nonMemberUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        ChannelResponse voiceChannel = studyServer.channels().stream()
                .filter(channel -> channel.kind().equals("VOICE"))
                .findFirst()
                .orElseThrow();

        MvcResult joinResult = mockMvc.perform(post(
                        "/api/v1/study-server-channels/{channelId}/voice-presences",
                        voiceChannel.id()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        VoicePresenceResponse joined = objectMapper.readValue(
                joinResult.getResponse().getContentAsString(),
                VoicePresenceResponse.class
        );

        assertThat(joined.channelId()).isEqualTo(voiceChannel.id());
        assertThat(joined.memberUserId()).isEqualTo(ownerUserId);
        assertThat(joined.canSpeak()).isTrue();
        assertThat(joined.canListen()).isTrue();

        MvcResult listResult = mockMvc.perform(get(
                        "/api/v1/study-server-channels/{channelId}/voice-presences",
                        voiceChannel.id()
                ).param("viewerUserId", ownerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        VoicePresenceListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                VoicePresenceListResponse.class
        );

        assertThat(listed.presences()).containsExactly(joined);

        mockMvc.perform(post(
                        "/api/v1/study-server-channels/{channelId}/voice-presences",
                        voiceChannel.id()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberUserId", nonMemberUserId.toString()
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                        "/api/v1/study-server-channels/{channelId}/voice-presences/{memberUserId}",
                        voiceChannel.id(),
                        ownerUserId
                ).param("actingUserId", nonMemberUserId.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                        "/api/v1/study-server-channels/{channelId}/voice-presences/{memberUserId}",
                        voiceChannel.id(),
                        ownerUserId
                ).param("actingUserId", ownerUserId.toString()))
                .andExpect(status().isNoContent());

        MvcResult afterLeaveResult = mockMvc.perform(get(
                        "/api/v1/study-server-channels/{channelId}/voice-presences",
                        voiceChannel.id()
                ).param("viewerUserId", ownerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        VoicePresenceListResponse afterLeave = objectMapper.readValue(
                afterLeaveResult.getResponse().getContentAsString(),
                VoicePresenceListResponse.class
        );

        assertThat(afterLeave.presences()).isEmpty();
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Java Spring Study Group",
                                "ownerUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private record StudyServerResponse(List<ChannelResponse> channels) {
    }

    private record ChannelResponse(UUID id, String name, String kind) {
    }

    private record VoicePresenceResponse(
            UUID channelId,
            UUID memberUserId,
            boolean canSpeak,
            boolean canListen
    ) {
    }

    private record VoicePresenceListResponse(List<VoicePresenceResponse> presences) {
    }
}
