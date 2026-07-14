package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
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
class StudyAssistantGrantCandidatesSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerAndInstructorCanListGrantCandidatesAndLearnerGetsViewerScope() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);

        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Spring Boot Foundations",
                                "cohortName", "Summer 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResponse course = objectMapper.readValue(
                courseResult.getResponse().getContentAsString(),
                CourseResponse.class
        );

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult ownerCandidatesResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates",
                        studyServer.id()
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        StudyAssistantGrantCandidatesResponse ownerCandidates = objectMapper.readValue(
                ownerCandidatesResult.getResponse().getContentAsString(),
                StudyAssistantGrantCandidatesResponse.class
        );

        assertThat(ownerCandidates.studyServerChannels())
                .extracting(StudyAssistantGrantCandidatesResponse.ChannelResponse::name)
                .contains("announcements", "general");
        assertThat(ownerCandidates.courses()).hasSize(1);
        assertThat(ownerCandidates.courses().getFirst().cohorts()).hasSize(1);
        assertThat(ownerCandidates.courses().getFirst().channels())
                .hasSize(4)
                .extracting(StudyAssistantGrantCandidatesResponse.ChannelResponse::name)
                .contains("study-room");

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates",
                        studyServer.id()
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates",
                        studyServer.id()
                ).with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates",
                        studyServer.id()
                ).with(asUser(strangerUserId)))
                .andExpect(status().isForbidden());

        MvcResult instructorScopeResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-viewer-scope",
                        studyServer.id()
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        StudyAssistantViewerScopeResponse instructorScope = objectMapper.readValue(
                instructorScopeResult.getResponse().getContentAsString(),
                StudyAssistantViewerScopeResponse.class
        );

        assertThat(instructorScope.canViewAllGrants()).isTrue();

        MvcResult learnerScopeResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-viewer-scope",
                        studyServer.id()
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        StudyAssistantViewerScopeResponse learnerScope = objectMapper.readValue(
                learnerScopeResult.getResponse().getContentAsString(),
                StudyAssistantViewerScopeResponse.class
        );

        assertThat(learnerScope.canViewAllGrants()).isFalse();
        assertThat(learnerScope.enrolledCourseIds()).containsExactly(course.id());
        assertThat(learnerScope.enrolledCohortIds()).containsExactly(course.cohort().id());
        assertThat(learnerScope.accessibleCourseChannelIds())
                .containsExactlyElementsOf(course.channels().stream().map(CourseChannelResponse::id).toList());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/study-assistant-viewer-scope",
                        studyServer.id()
                ).with(asUser(strangerUserId)))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Java Spring Study Group"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private record StudyServerResponse(UUID id) {
    }

    private record CourseResponse(
            UUID id,
            CohortResponse cohort,
            List<CourseChannelResponse> channels
    ) {
    }

    private record CohortResponse(UUID id) {
    }

    private record CourseChannelResponse(UUID id) {
    }
}
