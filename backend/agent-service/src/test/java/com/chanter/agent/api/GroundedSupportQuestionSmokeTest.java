package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ChannelCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CohortCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CourseCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.GrantCandidates;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.infra.TestCourseResourceCatalogClient;
import com.chanter.agent.infra.TestCourseResourceContentClient;
import com.chanter.agent.infra.TestStudyAssistantGrantCandidatesClient;
import com.chanter.agent.infra.TestStudyServerSaasPlanClient;
import com.chanter.agent.infra.TestSupportQuestionChannelAccessClient;
import com.chanter.agent.infra.TestSupportQuestionClient;
import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class GroundedSupportQuestionSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestStudyAssistantGrantCandidatesClient grantCandidatesClient;

    @Autowired
    private TestCourseResourceCatalogClient courseResourceCatalogClient;

    @Autowired
    private TestCourseResourceContentClient courseResourceContentClient;

    @Autowired
    private TestSupportQuestionChannelAccessClient channelAccessClient;

    @Autowired
    private TestSupportQuestionClient supportQuestionClient;

    @Autowired
    private TestStudyServerSaasPlanClient saasPlanClient;

    @BeforeEach
    void setUp() {
        grantCandidatesClient.clear();
        courseResourceCatalogClient.clear();
        courseResourceContentClient.clear();
        channelAccessClient.clear();
        supportQuestionClient.clear();
        saasPlanClient.clear();
    }

    @Test
    void learnerGetsGroundedAnswerWithSources() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                channelId,
                courseId,
                cohortId,
                courseResourceId
        );

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                channelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);
        courseResourceContentClient.registerContent(
                courseResourceId,
                """
                Spring Security uses a filter chain. Configure HttpSecurity to add authentication \
                and authorization rules for your endpoints.
                """.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        AssistantAnswerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AssistantAnswerResponse.class
        );

        assertThat(response.confidence()).isEqualTo("HIGH");
        assertThat(response.handoffRecommended()).isFalse();
        assertThat(response.supportQuestionStatus()).isEqualTo("AI_ANSWERED");
        assertThat(response.answerBody()).contains("Spring Security Guide");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().resourceId()).isEqualTo(courseResourceId);

        MvcResult reloadResult = mockMvc.perform(get(
                            "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                            channelId,
                            supportQuestionId
                        )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        AssistantAnswerResponse reloaded = objectMapper.readValue(
                reloadResult.getResponse().getContentAsString(),
                AssistantAnswerResponse.class
        );

        assertThat(reloaded.id()).isEqualTo(response.id());
        assertThat(reloaded.sources()).isEqualTo(response.sources());
    }

    @Test
    void unavailableGrantedResourceFallsBackToLowConfidenceAnswerAndHandoff() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                channelId,
                courseId,
                cohortId,
                courseResourceId
        );

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                channelId,
                learnerUserId,
                "What is the weather in Tokyo?"
        ));

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);
        courseResourceContentClient.registerUnavailable(courseResourceId);

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        AssistantAnswerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AssistantAnswerResponse.class
        );

        assertThat(response.confidence()).isEqualTo("LOW");
        assertThat(response.handoffRecommended()).isTrue();
        assertThat(response.supportQuestionStatus()).isEqualTo("AI_LOW_CONFIDENCE");
        assertThat(response.sources()).isEmpty();
        assertThat(response.answerBody()).contains("approved material");
    }

    @Test
    void ungrantedChannelReturnsForbidden() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID grantedChannelId = UUID.randomUUID();
        UUID deniedChannelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        installAssistant(
                studyServerId,
                instructorUserId,
                learnerUserId,
                grantedChannelId,
                courseId,
                cohortId,
                courseResourceId
        );

        channelAccessClient.grantLearnerPost(deniedChannelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                deniedChannelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));

        mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        deniedChannelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uninstalledAssistantReturnsNotFound() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID supportQuestionId = UUID.randomUUID();

        grantCandidatesClient.registerViewerScope(
                studyServerId,
                learnerUserId,
                TestStudyAssistantGrantCandidatesClient.learnerScope(
                        studyServerId,
                        Set.of(courseId),
                        Set.of(),
                        Set.of(channelId)
                )
        );

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                supportQuestionId,
                channelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));

        mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        supportQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isNotFound());
    }

    private void installAssistant(
            UUID studyServerId,
            UUID instructorUserId,
            UUID learnerUserId,
            UUID channelId,
            UUID courseId,
            UUID cohortId,
            UUID courseResourceId
    ) throws Exception {
        grantCandidatesClient.registerGrantCandidates(
                studyServerId,
                instructorUserId,
                new GrantCandidates(
                        studyServerId,
                        List.of(),
                        List.of(new CourseCandidate(
                                courseId,
                                "Spring Boot Foundations",
                                List.of(new CohortCandidate(cohortId, "Summer 2026")),
                                List.of(new ChannelCandidate(channelId, "questions", "TEXT"))
                        ))
                )
        );
        grantCandidatesClient.registerViewerScope(
                studyServerId,
                instructorUserId,
                TestStudyAssistantGrantCandidatesClient.instructorScope(studyServerId)
        );
        grantCandidatesClient.registerViewerScope(
                studyServerId,
                learnerUserId,
                TestStudyAssistantGrantCandidatesClient.learnerScope(
                        studyServerId,
                        Set.of(courseId),
                        Set.of(cohortId),
                        Set.of(channelId)
                )
        );

        saasPlanClient.registerPlan(studyServerId, "STARTER", 100);

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "grants", List.of(
                                        Map.of(
                                                "grantType", GrantType.COURSE_CHANNEL.name(),
                                                "grantTargetId", channelId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE_RESOURCE.name(),
                                                "grantTargetId", courseResourceId.toString()
                                        )
                                )
                        ))))
                .andExpect(status().isCreated());
    }
}
