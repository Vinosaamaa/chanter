package com.chanter.agent.api;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiQuotaSmokeTest {

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
    void quotaExhaustionBlocksAdditionalAssistantInvocations() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();
        UUID firstQuestionId = UUID.randomUUID();
        UUID secondQuestionId = UUID.randomUUID();

        installAssistant(studyServerId, instructorUserId, learnerUserId, channelId, courseId, cohortId, courseResourceId);
        saasPlanClient.registerPlan(studyServerId, "STARTER", 1);

        channelAccessClient.grantLearnerPost(channelId, learnerUserId, courseId, studyServerId, "questions");
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);
        courseResourceContentClient.registerContent(
                courseResourceId,
                "Spring Security uses filters.".getBytes(StandardCharsets.UTF_8)
        );

        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                firstQuestionId,
                channelId,
                learnerUserId,
                "How do I configure Spring Security filters?"
        ));
        supportQuestionClient.registerSupportQuestion(TestSupportQuestionClient.unanswered(
                secondQuestionId,
                channelId,
                learnerUserId,
                "How do I add method security?"
        ));

        mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        firstQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                        "/api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer",
                        channelId,
                        secondQuestionId
                )
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isTooManyRequests());
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

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .content(objectMapper.writeValueAsString(Map.of(
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
