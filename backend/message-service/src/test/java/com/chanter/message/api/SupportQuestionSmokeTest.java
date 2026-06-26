package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
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
class SupportQuestionSmokeTest {

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
    void enrolledLearnerCanPostSupportQuestionAndInstructorCanListUnansweredOnes() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        courseChannelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");

        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "How do I configure Spring Security?",
                                "idempotencyKey", idempotencyKey
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        SupportQuestionResponse created = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );

        assertThat(created.channelId()).isEqualTo(channelId);
        assertThat(created.senderUserId()).isEqualTo(learnerUserId);
        assertThat(created.body()).isEqualTo("How do I configure Spring Security?");
        assertThat(created.status()).isEqualTo("UNANSWERED");
        assertThat(created.idempotencyKey()).isEqualTo(idempotencyKey);

        MvcResult retryResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "How do I configure Spring Security?",
                                "idempotencyKey", idempotencyKey
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        SupportQuestionResponse retried = objectMapper.readValue(
                retryResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );

        assertThat(retried.id()).isEqualTo(created.id());

        MvcResult listResult = mockMvc.perform(get("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        SupportQuestionListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                SupportQuestionListResponse.class
        );

        assertThat(listed.supportQuestions()).containsExactly(
                new SupportQuestionSummaryResponse(
                        created.id(),
                        created.channelMessageId(),
                        created.channelId(),
                        created.senderUserId(),
                        created.body(),
                        created.status(),
                        created.createdAt()
                )
        );
    }

    @Test
    void unauthorizedUserCannotPostSupportQuestion() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        courseChannelAccessClient.registerChannel(channelId);
        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, UUID.randomUUID(), "questions");

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, strangerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Can I post here?",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void postingToUnknownCourseChannelReturnsNotFound() throws Exception {
        UUID senderUserId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Hello?",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void instructorCannotPostSupportQuestion() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");

        mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Instructor question",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isForbidden());
    }
}
