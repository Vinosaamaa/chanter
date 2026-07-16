package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.domain.SupportQuestionStatus;
import com.chanter.message.infra.TestCourseChannelAccessClient;
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
class ChannelSummarySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCourseChannelAccessClient courseChannelAccessClient;

    @BeforeEach
    void setUp() {
        courseChannelAccessClient.clear();
    }

    @Test
    void instructorCanGenerateChannelSummaryWithTopicsAndFollowUps() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        courseChannelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");

        postSupportQuestion(channelId, learnerUserId, "How do I configure Spring Security OAuth2?");
        postSupportQuestion(channelId, learnerUserId, "Spring Security OAuth2 setup for REST APIs?");
        postSupportQuestion(channelId, learnerUserId, "Deployment checklist for Spring Boot apps?");

        MvcResult summaryResult = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/channel-summary",
                        channelId)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("windowDays", 7))))
                .andExpect(status().isOk())
                .andReturn();

        ChannelSummaryResponse summary = objectMapper.readValue(
                summaryResult.getResponse().getContentAsString(),
                ChannelSummaryResponse.class
        );

        assertThat(summary.channelId()).isEqualTo(channelId);
        assertThat(summary.channelName()).isEqualTo("questions");
        assertThat(summary.windowDays()).isEqualTo(7);
        assertThat(summary.metrics().questionsAsked().value()).isEqualTo(3);
        assertThat(summary.metrics().replies().value()).isZero();
        assertThat(summary.digest().topTopics().title()).isNotBlank();
        assertThat(summary.digest().unansweredFollowUps().count()).isGreaterThanOrEqualTo(3);
        assertThat(summary.timeline()).isNotEmpty();
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        UUID channelId = UUID.randomUUID();
        courseChannelAccessClient.registerChannel(channelId);

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/channel-summary", channelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("windowDays", 7))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void learnerCannotGenerateChannelSummary() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/channel-summary", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("windowDays", 7))))
                .andExpect(status().isForbidden());
    }

    private void postSupportQuestion(UUID channelId, UUID learnerUserId, String body) throws Exception {
        MvcResult postResult = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions",
                        channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, "test-internal-service-token-for-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", body,
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        SupportQuestionResponse created = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );
        assertThat(created.status()).isEqualTo(SupportQuestionStatus.UNANSWERED.name());
    }
}
