package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.message.infra.TestCourseChannelAccessClient;
import com.chanter.message.infra.TestNotificationClient;
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

    @Autowired
    private TestNotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        courseChannelAccessClient.clear();
        notificationClient.clear();
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

    @Test
    void instructorReplyIsPersistedAndVisibleToTheQuestionAuthor() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        courseChannelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");

        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Why does the recursive call stop?",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        SupportQuestionResponse question = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );

        mockMvc.perform(post(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/replies",
                            channelId,
                            question.id()
                        )
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "It stops when the base case returns without another call."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorUserId").value(instructorUserId.toString()))
                .andExpect(jsonPath("$.body").value(
                        "It stops when the base case returns without another call."
                ));

        mockMvc.perform(get(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/replies",
                            channelId,
                            question.id()
                        )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replies.length()").value(1))
                .andExpect(jsonPath("$.replies[0].authorUserId").value(instructorUserId.toString()))
                .andExpect(jsonPath("$.replies[0].body").value(
                        "It stops when the base case returns without another call."
                ));

        mockMvc.perform(get(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}",
                            channelId,
                            question.id()
                        )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HUMAN_ANSWERED"));

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportQuestions.length()").value(1))
                .andExpect(jsonPath("$.supportQuestions[0].id").value(question.id().toString()))
                .andExpect(jsonPath("$.supportQuestions[0].status").value("HUMAN_ANSWERED"));

        assertThat(notificationClient.calls()).hasSize(1);
        assertThat(notificationClient.calls().getFirst().recipientUserId()).isEqualTo(learnerUserId);
        assertThat(notificationClient.calls().getFirst().supportQuestionId()).isEqualTo(question.id());
        assertThat(notificationClient.calls().getFirst().channelId()).isEqualTo(channelId);
        assertThat(notificationClient.calls().getFirst().courseId()).isEqualTo(courseId);
        assertThat(notificationClient.calls().getFirst().bodyPreview())
                .isEqualTo("It stops when the base case returns without another call.");
    }

    @Test
    void onlyTeachingStaffCanMarkSupportQuestionAsDuplicate() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        courseChannelAccessClient.grantInstructorView(channelId, instructorUserId, courseId, "questions");

        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Is this the same recursion question?",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        SupportQuestionResponse question = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );

        mockMvc.perform(patch(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/moderation",
                            channelId,
                            question.id()
                        )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DUPLICATE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/moderation",
                            channelId,
                            question.id()
                        )
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DUPLICATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        mockMvc.perform(post(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/replies",
                            channelId,
                            question.id()
                        )
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "A late reply"))))
                .andExpect(status().isConflict());
    }
}
