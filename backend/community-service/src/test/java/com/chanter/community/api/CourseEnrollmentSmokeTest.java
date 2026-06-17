package com.chanter.community.api;

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
class CourseEnrollmentSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void learnerAccessesCourseChannelsOnlyThroughCohortEnrollment() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID nonEnrolledUserId = UUID.randomUUID();

        StudyServerResponse studyServer = createStudyServer(ownerUserId);

        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerUserId", ownerUserId.toString(),
                                "title", "Spring Boot Foundations",
                                "instructorUserId", instructorUserId.toString(),
                                "cohortName", "Summer 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResponse course = objectMapper.readValue(
                courseResult.getResponse().getContentAsString(),
                CourseResponse.class
        );

        assertThat(course.title()).isEqualTo("Spring Boot Foundations");
        assertThat(course.instructorRole()).isEqualTo(new InstructorRoleResponse(instructorUserId, "INSTRUCTOR"));
        assertThat(course.cohort().name()).isEqualTo("Summer 2026");
        assertThat(course.channels())
                .extracting(CourseChannelResponse::name)
                .containsExactly("announcements", "questions", "resources");

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", course.channels().getFirst().id())
                        .param("viewerUserId", instructorUserId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", nonEnrolledUserId.toString(),
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString(),
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult channelResult = mockMvc.perform(get(
                        "/api/v1/course-channels/{channelId}", course.channels().getFirst().id()
                ).param("viewerUserId", learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        CourseChannelResponse accessibleChannel = objectMapper.readValue(
                channelResult.getResponse().getContentAsString(),
                CourseChannelResponse.class
        );

        assertThat(accessibleChannel.name()).isEqualTo("announcements");

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", course.channels().getFirst().id())
                        .param("viewerUserId", nonEnrolledUserId.toString()))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Java Spring Study Group",
                                "ownerUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private record StudyServerResponse(UUID id) {
    }

    private record CourseResponse(
            UUID id,
            String title,
            InstructorRoleResponse instructorRole,
            CohortResponse cohort,
            List<CourseChannelResponse> channels
    ) {
    }

    private record InstructorRoleResponse(UUID userId, String role) {
    }

    private record CohortResponse(UUID id, String name) {
    }

    private record CourseChannelResponse(UUID id, String name, String kind) {
    }
}
