package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.domain.ChannelScope;
import com.chanter.message.infra.TestChannelMessageAccessClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class ChannelMessageSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestChannelMessageAccessClient channelMessageAccessClient;

    @BeforeEach
    void setUp() {
        channelMessageAccessClient.clear();
    }

    @Test
    void learnerCanPostAndListCourseChannelMessages() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        channelMessageAccessClient.grant(channelId, learnerUserId, ChannelScope.COURSE);

        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/messages", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Hello from #questions"))))
                .andExpect(status().isCreated())
                .andReturn();

        ChannelMessageResponse created = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                ChannelMessageResponse.class
        );

        assertThat(created.channelId()).isEqualTo(channelId);
        assertThat(created.senderUserId()).isEqualTo(learnerUserId);
        assertThat(created.body()).isEqualTo("Hello from #questions");

        MvcResult listResult = mockMvc.perform(get("/api/v1/course-channels/{channelId}/messages", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        ChannelMessageListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                ChannelMessageListResponse.class
        );

        assertThat(listed.messages()).containsExactly(created);
    }

    @Test
    void unauthorizedUserCannotPostCourseChannelMessage() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/messages", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "blocked"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanPostAndListStudyServerChannelMessages() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        channelMessageAccessClient.grant(channelId, ownerUserId, ChannelScope.STUDY_SERVER);

        MvcResult postResult = mockMvc.perform(post("/api/v1/study-server-channels/{channelId}/messages", channelId)
                        .header(AuthHeaders.USER_ID, ownerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Hello #general"))))
                .andExpect(status().isCreated())
                .andReturn();

        ChannelMessageResponse created = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                ChannelMessageResponse.class
        );

        assertThat(created.channelId()).isEqualTo(channelId);
        assertThat(created.senderUserId()).isEqualTo(ownerUserId);
        assertThat(created.body()).isEqualTo("Hello #general");

        MvcResult listResult = mockMvc.perform(get("/api/v1/study-server-channels/{channelId}/messages", channelId)
                        .header(AuthHeaders.USER_ID, ownerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        ChannelMessageListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                ChannelMessageListResponse.class
        );

        assertThat(listed.messages()).containsExactly(created);
    }

    @Test
    void unauthorizedUserCannotPostStudyServerChannelMessage() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/study-server-channels/{channelId}/messages", channelId)
                        .header(AuthHeaders.USER_ID, ownerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "blocked"))))
                .andExpect(status().isForbidden());
    }
}
