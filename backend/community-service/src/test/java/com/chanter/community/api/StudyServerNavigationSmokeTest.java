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
class StudyServerNavigationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerAndLearnerSeeDifferentNavigationSidebars() throws Exception {
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

        MvcResult secondCourseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Data Science Basics",
                                "cohortName", "Fall 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResponse secondCourse = objectMapper.readValue(
                secondCourseResult.getResponse().getContentAsString(),
                CourseResponse.class
        );

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult ownerListResult = mockMvc.perform(get("/api/v1/study-servers").with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        List<AccessibleStudyServerResponse> ownerList = objectMapper.readValue(
                ownerListResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AccessibleStudyServerResponse.class)
        );
        assertThat(ownerList).extracting(AccessibleStudyServerResponse::id).contains(studyServer.id());

        MvcResult learnerListResult = mockMvc.perform(get("/api/v1/study-servers").with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        List<AccessibleStudyServerResponse> learnerList = objectMapper.readValue(
                learnerListResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AccessibleStudyServerResponse.class)
        );
        assertThat(learnerList).extracting(AccessibleStudyServerResponse::id).contains(studyServer.id());

        mockMvc.perform(get("/api/v1/study-servers").with(asUser(strangerUserId)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("[]"));

        MvcResult ownerNavigationResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
                        studyServer.id()
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        StudyServerNavigationResponse ownerNavigation = objectMapper.readValue(
                ownerNavigationResult.getResponse().getContentAsString(),
                StudyServerNavigationResponse.class
        );

        assertThat(ownerNavigation.studyServerName()).isEqualTo("Java Spring Study Group");
        assertThat(ownerNavigation.studyServerChannels())
                .extracting(StudyAssistantGrantCandidatesResponse.ChannelResponse::name)
                .contains("announcements", "general");
        assertThat(ownerNavigation.courses()).hasSize(2);

        MvcResult learnerNavigationResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
                        studyServer.id()
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        StudyServerNavigationResponse learnerNavigation = objectMapper.readValue(
                learnerNavigationResult.getResponse().getContentAsString(),
                StudyServerNavigationResponse.class
        );

        assertThat(learnerNavigation.studyServerChannels()).isEmpty();
        assertThat(learnerNavigation.courses()).hasSize(1);
        assertThat(learnerNavigation.courses().getFirst().id()).isEqualTo(course.id());
        assertThat(learnerNavigation.courses().getFirst().channels()).hasSize(3);
        assertThat(learnerNavigation.courses().stream().map(StudyAssistantGrantCandidatesResponse.CourseResponse::id))
                .doesNotContain(secondCourse.id());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
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
