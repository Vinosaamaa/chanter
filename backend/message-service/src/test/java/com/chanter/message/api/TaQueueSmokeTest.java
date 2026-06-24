package com.chanter.message.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.message.domain.SupportQuestionStatus;
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
    void learnerCanQueueLowConfidenceSupportQuestionAndTaCanResolveIt() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID studyServerId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        cohortTaQueueAccessClient.grantLearnerAdd(cohortId, learnerUserId, courseId, studyServerId);
        cohortTaQueueAccessClient.grantInstructorManage(cohortId, instructorUserId, courseId, studyServerId);

        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", learnerUserId.toString(),
                                "body", "How do I configure Spring Security?",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        SupportQuestionResponse supportQuestion = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );

        mockMvc.perform(patch(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/status",
                        channelId,
                        supportQuestion.id()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", learnerUserId.toString(),
                                "status", SupportQuestionStatus.AI_LOW_CONFIDENCE.name()
                        ))))
                .andExpect(status().isOk());

        MvcResult addResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString(),
                                "supportQuestionId", supportQuestion.id().toString(),
                                "channelId", channelId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        TaQueueItemResponse queued = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                TaQueueItemResponse.class
        );

        assertThat(queued.status()).isEqualTo("OPEN");
        assertThat(queued.supportQuestionId()).isEqualTo(supportQuestion.id());

        MvcResult listResult = mockMvc.perform(get("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        TaQueueListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                TaQueueListResponse.class
        );
        assertThat(listed.taQueueItems()).extracting(TaQueueItemResponse::id).containsExactly(queued.id());

        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/ta-queue/{itemId}/pickup", cohortId, queued.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isOk());

        MvcResult resolveResult = mockMvc.perform(patch(
                        "/api/v1/cohorts/{cohortId}/ta-queue/{itemId}/resolve",
                        cohortId,
                        queued.id()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        TaQueueItemResponse resolved = objectMapper.readValue(
                resolveResult.getResponse().getContentAsString(),
                TaQueueItemResponse.class
        );

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.assignedTaUserId()).isEqualTo(instructorUserId);
    }

    @Test
    void strangerCannotListTaQueue() throws Exception {
        UUID cohortId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID studyServerId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        cohortTaQueueAccessClient.registerCohort(cohortId, courseId, studyServerId);

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .param("viewerUserId", strangerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void strangerCannotPickupOrResolveTaQueueItem() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID studyServerId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        courseChannelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, "questions");
        cohortTaQueueAccessClient.grantLearnerAdd(cohortId, learnerUserId, courseId, studyServerId);
        cohortTaQueueAccessClient.grantInstructorManage(cohortId, instructorUserId, courseId, studyServerId);
        cohortTaQueueAccessClient.registerCohort(cohortId, courseId, studyServerId);

        MvcResult postResult = mockMvc.perform(post("/api/v1/course-channels/{channelId}/support-questions", channelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderUserId", learnerUserId.toString(),
                                "body", "Need help with Spring Security",
                                "idempotencyKey", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        SupportQuestionResponse supportQuestion = objectMapper.readValue(
                postResult.getResponse().getContentAsString(),
                SupportQuestionResponse.class
        );

        mockMvc.perform(patch(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/status",
                        channelId,
                        supportQuestion.id()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", learnerUserId.toString(),
                                "status", SupportQuestionStatus.AI_LOW_CONFIDENCE.name()
                        ))))
                .andExpect(status().isOk());

        MvcResult addResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/ta-queue", cohortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString(),
                                "supportQuestionId", supportQuestion.id().toString(),
                                "channelId", channelId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        TaQueueItemResponse queued = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                TaQueueItemResponse.class
        );

        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/ta-queue/{itemId}/pickup", cohortId, queued.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", strangerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/ta-queue/{itemId}/resolve", cohortId, queued.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actorUserId", strangerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());
    }
}
