package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.ChannelCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CohortCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.CourseCandidate;
import com.chanter.agent.application.StudyAssistantGrantCandidatesClient.GrantCandidates;
import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.infra.TestCourseResourceCatalogClient;
import com.chanter.agent.infra.TestStudyAssistantGrantCandidatesClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class StudyAssistantInstallSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestStudyAssistantGrantCandidatesClient grantCandidatesClient;

    @Autowired
    private TestCourseResourceCatalogClient courseResourceCatalogClient;

    @BeforeEach
    void setUp() {
        grantCandidatesClient.clear();
        courseResourceCatalogClient.clear();
    }

    @Test
    void instructorPreviewsInstallConfirmsGrantsAndLearnerSeesScopedPresence() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID studyServerChannelId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        UUID courseChannelId = UUID.randomUUID();
        UUID courseResourceId = UUID.randomUUID();

        GrantCandidates candidates = new GrantCandidates(
                studyServerId,
                List.of(new ChannelCandidate(studyServerChannelId, "general", "TEXT")),
                List.of(new CourseCandidate(
                        courseId,
                        "Spring Boot Foundations",
                        List.of(new CohortCandidate(cohortId, "Summer 2026")),
                        List.of(new ChannelCandidate(courseChannelId, "questions", "TEXT"))
                ))
        );

        grantCandidatesClient.registerGrantCandidates(studyServerId, instructorUserId, candidates);
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
                        Set.of(courseChannelId)
                )
        );

        courseResourceCatalogClient.registerResource(new CourseResourceSummary(
                courseResourceId,
                courseId,
                "Spring Security Guide",
                "spring-security-guide.md",
                true
        ));
        courseResourceCatalogClient.grantViewerAccess(courseId, instructorUserId);
        courseResourceCatalogClient.grantViewerAccess(courseId, learnerUserId);

        MvcResult previewResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant/install-preview",
                        studyServerId
                ).param("instructorUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        InstallPreviewResponse preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(),
                InstallPreviewResponse.class
        );

        assertThat(preview.alreadyInstalled()).isFalse();
        assertThat(preview.candidates().studyServerChannels()).hasSize(1);
        assertThat(preview.candidates().courses()).hasSize(1);
        assertThat(preview.courseResources()).hasSize(1);

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "grants", List.of(
                                        Map.of(
                                                "grantType", GrantType.STUDY_SERVER_CHANNEL.name(),
                                                "grantTargetId", studyServerChannelId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE.name(),
                                                "grantTargetId", courseId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COHORT.name(),
                                                "grantTargetId", cohortId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE_CHANNEL.name(),
                                                "grantTargetId", courseChannelId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE_RESOURCE.name(),
                                                "grantTargetId", courseResourceId.toString()
                                        )
                                )
                        ))))
                .andExpect(status().isCreated());

        MvcResult instructorPresenceResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant", studyServerId
                ).param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        StudyAssistantPresenceResponse instructorPresence = objectMapper.readValue(
                instructorPresenceResult.getResponse().getContentAsString(),
                StudyAssistantPresenceResponse.class
        );

        assertThat(instructorPresence.installed()).isTrue();
        assertThat(instructorPresence.grants()).hasSize(5);

        MvcResult learnerPresenceResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant", studyServerId
                ).param("viewerUserId", learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        StudyAssistantPresenceResponse learnerPresence = objectMapper.readValue(
                learnerPresenceResult.getResponse().getContentAsString(),
                StudyAssistantPresenceResponse.class
        );

        assertThat(learnerPresence.installed()).isTrue();
        assertThat(learnerPresence.grants())
                .extracting(StudyAssistantPresenceResponse.GrantResponse::grantType)
                .containsExactlyInAnyOrder(
                        GrantType.STUDY_SERVER_CHANNEL,
                        GrantType.COURSE,
                        GrantType.COHORT,
                        GrantType.COURSE_CHANNEL,
                        GrantType.COURSE_RESOURCE
                );
    }

    @Test
    void installRejectsGrantsOutsidePreviewCandidates() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID allowedChannelId = UUID.randomUUID();
        UUID unknownChannelId = UUID.randomUUID();

        grantCandidatesClient.registerGrantCandidates(
                studyServerId,
                instructorUserId,
                new GrantCandidates(
                        studyServerId,
                        List.of(new ChannelCandidate(allowedChannelId, "general", "TEXT")),
                        List.of()
                )
        );

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "grants", List.of(Map.of(
                                        "grantType", GrantType.STUDY_SERVER_CHANNEL.name(),
                                        "grantTargetId", unknownChannelId.toString()
                                ))
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondInstallReturnsConflict() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        grantCandidatesClient.registerGrantCandidates(
                studyServerId,
                instructorUserId,
                new GrantCandidates(
                        studyServerId,
                        List.of(new ChannelCandidate(channelId, "general", "TEXT")),
                        List.of()
                )
        );
        grantCandidatesClient.registerViewerScope(
                studyServerId,
                instructorUserId,
                TestStudyAssistantGrantCandidatesClient.instructorScope(studyServerId)
        );

        String installBody = objectMapper.writeValueAsString(Map.of(
                "instructorUserId", instructorUserId.toString(),
                "grants", List.of(Map.of(
                        "grantType", GrantType.STUDY_SERVER_CHANNEL.name(),
                        "grantTargetId", channelId.toString()
                ))
        ));

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(installBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(installBody))
                .andExpect(status().isConflict());
    }

    @Test
    void learnerPresenceHidesGrantsOutsideEnrollmentScope() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID enrolledCourseId = UUID.randomUUID();
        UUID otherCourseId = UUID.randomUUID();
        UUID enrolledCohortId = UUID.randomUUID();
        UUID studyServerChannelId = UUID.randomUUID();

        grantCandidatesClient.registerGrantCandidates(
                studyServerId,
                instructorUserId,
                new GrantCandidates(
                        studyServerId,
                        List.of(new ChannelCandidate(studyServerChannelId, "general", "TEXT")),
                        List.of(
                                new CourseCandidate(
                                        enrolledCourseId,
                                        "Enrolled Course",
                                        List.of(new CohortCandidate(enrolledCohortId, "Summer 2026")),
                                        List.of()
                                ),
                                new CourseCandidate(otherCourseId, "Other Course", List.of(), List.of())
                        )
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
                        Set.of(enrolledCourseId),
                        Set.of(enrolledCohortId),
                        Set.of()
                )
        );

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/study-assistant/install", studyServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "grants", List.of(
                                        Map.of(
                                                "grantType", GrantType.STUDY_SERVER_CHANNEL.name(),
                                                "grantTargetId", studyServerChannelId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE.name(),
                                                "grantTargetId", enrolledCourseId.toString()
                                        ),
                                        Map.of(
                                                "grantType", GrantType.COURSE.name(),
                                                "grantTargetId", otherCourseId.toString()
                                        )
                                )
                        ))))
                .andExpect(status().isCreated());

        MvcResult learnerPresenceResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant", studyServerId
                ).param("viewerUserId", learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        StudyAssistantPresenceResponse learnerPresence = objectMapper.readValue(
                learnerPresenceResult.getResponse().getContentAsString(),
                StudyAssistantPresenceResponse.class
        );

        assertThat(learnerPresence.grants())
                .extracting(StudyAssistantPresenceResponse.GrantResponse::grantType)
                .contains(GrantType.STUDY_SERVER_CHANNEL, GrantType.COURSE);
        assertThat(learnerPresence.grants())
                .filteredOn(grant -> grant.grantType() == GrantType.COURSE)
                .extracting(StudyAssistantPresenceResponse.GrantResponse::grantTargetId)
                .containsExactly(enrolledCourseId);
    }

    @Test
    void unauthorizedViewerCannotSeePresence() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/study-assistant", studyServerId)
                        .param("viewerUserId", strangerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uninstalledStudyServerReportsInstalledFalseForInstructor() throws Exception {
        UUID studyServerId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        grantCandidatesClient.registerViewerScope(
                studyServerId,
                instructorUserId,
                TestStudyAssistantGrantCandidatesClient.instructorScope(studyServerId)
        );

        MvcResult presenceResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant", studyServerId
                ).param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        StudyAssistantPresenceResponse presence = objectMapper.readValue(
                presenceResult.getResponse().getContentAsString(),
                StudyAssistantPresenceResponse.class
        );

        assertThat(presence.installed()).isFalse();
        assertThat(presence.grants()).isEmpty();
    }
}
