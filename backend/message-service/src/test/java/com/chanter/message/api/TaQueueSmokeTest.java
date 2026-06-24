package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.message.infra.TestCohortTaQueueAccessClient;
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
class TaQueueSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCourseChannelAccessClient courseChannelAccessClient;

    @Autowired
    private TestCohortTaQueueAccessClient cohortTaQueueAccessClient;

    @BeforeEach
    void setUp() {
        courseChannelAccessClient.clear();
        cohortTaQueueAccessClient.clear();
    }

    @Test
    void learnerCanRouteLowConfidenceSupportQuestionToTaQueueAndInstructorCanResolveIt() throws Exception {
        UUID cohortId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        cohortTaQueueAccessClient.grantLearnerAdd(cohortId, learnerUserId, courseId);
        cohortTaQueueAccessClient.grantInstructorManage(cohortId, instructorUserId, courseId);

        SupportQuestionResponse supportQuestion = postSupportQuestion(channelId, learnerUserId);
        markLowConfidence(channelId, supportQuestion.id(), learnerUserId);

        MvcResult addResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channelId", channelId.toString(),
                                "supportQuestionId", supportQuestion.id().toString(),
                                "actorUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        TaQueueItemResponse created = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                TaQueueItemResponse.class
        );

        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.supportQuestionId()).isEqualTo(supportQuestion.id());

        MvcResult listResult = mockMvc.perform(get("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        TaQueueListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                TaQueueListResponse.class
        );

        assertThat(listed.items()).hasSize(1);

        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/ta-queue/{itemId}/pickup", cohortId, created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/ta-queue/{itemId}/resolve", cohortId, created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isOk());

        MvcResult emptyListResult = mockMvc.perform(get("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        TaQueueListResponse emptyListed = objectMapper.readValue(
                emptyListResult.getResponse().getContentAsString(),
                TaQueueListResponse.class
        );

        assertThat(emptyListed.items()).isEmpty();
    }

    @Test
    void unauthorizedUserCannotListTaQueue() throws Exception {
        UUID cohortId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        cohortTaQueueAccessClient.grantLearnerAdd(cohortId, learnerUserId, courseId);

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .param("viewerUserId", strangerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    private SupportQuestionResponse postSupportQuestion(UUID channelId, UUID learnerUserId) throws Exception {
        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", learnerUserId.toString(),
                                "body", "How do I configure Spring Security?",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(postResult.getResponse().getContentAsString(), SupportQuestionResponse.class);
    }

    private void markLowConfidence(UUID channelId, UUID supportQuestionId, UUID learnerUserId) throws Exception {
        mockMvc.perform(patch(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/status",
                        channelId,
                        supportQuestionId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", learnerUserId.toString(),
                                "status", "AI_LOW_CONFIDENCE"
                        ))))
                .andExpect(status().isOk());
    }
}
