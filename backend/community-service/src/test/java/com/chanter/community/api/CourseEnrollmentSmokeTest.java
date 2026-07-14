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
import org.springframework.jdbc.core.simple.JdbcClient;
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

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void learnerAccessesCourseChannelsOnlyThroughCohortEnrollment() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID nonEnrolledUserId = UUID.randomUUID();

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

        assertThat(course.title()).isEqualTo("Spring Boot Foundations");
        assertThat(course.instructorRole()).isEqualTo(new InstructorRoleResponse(ownerUserId, "INSTRUCTOR"));
        assertThat(course.cohort().name()).isEqualTo("Summer 2026");
        assertThat(course.channels())
                .extracting(CourseChannelResponse::name)
                .containsExactly("announcements", "questions", "resources", "study-room");
        assertThat(course.channels().getLast().kind()).isEqualTo("VOICE");

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", course.channels().getFirst().id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", UUID.randomUUID())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", UUID.randomUUID())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(nonEnrolledUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isForbidden());

        UUID inviteLearnerUserId = UUID.randomUUID();
        MvcResult inviteResult = mockMvc.perform(get("/api/v1/cohorts/{cohortId}/invite", course.cohort().id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CohortInviteResponse invite = objectMapper.readValue(
                inviteResult.getResponse().getContentAsString(),
                CohortInviteResponse.class
        );
        setEnrollmentPolicy(course.cohort().id(), "INVITE_ONLY");

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", course.cohort().id())
                        .with(asUser(inviteLearnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inviteCode", invite.inviteCode().toString()
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", course.channels().getFirst().id())
                        .with(asUser(inviteLearnerUserId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());

        MvcResult enrollmentsResult = mockMvc.perform(get("/api/v1/cohorts/{cohortId}/enrollments", course.cohort().id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CohortEnrollmentListResponse enrollments = objectMapper.readValue(
                enrollmentsResult.getResponse().getContentAsString(),
                CohortEnrollmentListResponse.class
        );
        assertThat(enrollments.enrollments())
                .extracting(CohortEnrollmentResponse::learnerUserId)
                .contains(learnerUserId);
        assertThat(enrollments.totalCount()).isEqualTo(2);

        MvcResult channelResult = mockMvc.perform(get(
                        "/api/v1/course-channels/{channelId}", course.channels().getFirst().id()
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CourseChannelResponse accessibleChannel = objectMapper.readValue(
                channelResult.getResponse().getContentAsString(),
                CourseChannelResponse.class
        );

        assertThat(accessibleChannel.name()).isEqualTo("announcements");

        UUID questionsChannelId = course.channels().stream()
                .filter(channel -> channel.name().equals("questions"))
                .findFirst()
                .orElseThrow()
                .id();

        MvcResult learnerAccessResult = mockMvc.perform(get(
                        "/api/v1/course-channels/{channelId}/support-question-access", questionsChannelId
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        SupportQuestionChannelAccessResponse learnerAccess = objectMapper.readValue(
                learnerAccessResult.getResponse().getContentAsString(),
                SupportQuestionChannelAccessResponse.class
        );

        assertThat(learnerAccess.canPostSupportQuestion()).isTrue();
        assertThat(learnerAccess.canViewUnansweredSupportQuestions()).isFalse();

        UUID announcementsChannelId = course.channels().stream()
                .filter(channel -> channel.name().equals("announcements"))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(get(
                        "/api/v1/course-channels/{channelId}/support-question-access", announcementsChannelId
                ).with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());

        MvcResult instructorAccessResult = mockMvc.perform(get(
                        "/api/v1/course-channels/{channelId}/support-question-access", questionsChannelId
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        SupportQuestionChannelAccessResponse instructorAccess = objectMapper.readValue(
                instructorAccessResult.getResponse().getContentAsString(),
                SupportQuestionChannelAccessResponse.class
        );

        assertThat(instructorAccess.canPostSupportQuestion()).isFalse();
        assertThat(instructorAccess.canViewUnansweredSupportQuestions()).isTrue();

        MvcResult learnerResourceAccessResult = mockMvc.perform(get(
                        "/api/v1/courses/{courseId}/resource-access", course.id()
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CourseResourceAccessResponse learnerResourceAccess = objectMapper.readValue(
                learnerResourceAccessResult.getResponse().getContentAsString(),
                CourseResourceAccessResponse.class
        );

        assertThat(learnerResourceAccess.canUploadCourseResource()).isFalse();
        assertThat(learnerResourceAccess.canViewCourseResources()).isTrue();

        MvcResult instructorResourceAccessResult = mockMvc.perform(get(
                        "/api/v1/courses/{courseId}/resource-access", course.id()
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CourseResourceAccessResponse instructorResourceAccess = objectMapper.readValue(
                instructorResourceAccessResult.getResponse().getContentAsString(),
                CourseResourceAccessResponse.class
        );

        assertThat(instructorResourceAccess.canUploadCourseResource()).isTrue();
        assertThat(instructorResourceAccess.canViewCourseResources()).isTrue();

        mockMvc.perform(get("/api/v1/courses/{courseId}/resource-access", course.id())
                        .with(asUser(nonEnrolledUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/support-question-access", questionsChannelId)
                        .with(asUser(nonEnrolledUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", course.channels().getFirst().id())
                        .with(asUser(nonEnrolledUserId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void joinCohortRejectsInvalidInviteCode() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);

        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Algorithms",
                                "cohortName", "Fall 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResponse course = objectMapper.readValue(
                courseResult.getResponse().getContentAsString(),
                CourseResponse.class
        );
        setEnrollmentPolicy(course.cohort().id(), "INVITE_ONLY");

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", course.cohort().id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inviteCode", UUID.randomUUID().toString()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCohortEnrollmentsSupportsServerSideSearch() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);

        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Databases",
                                "cohortName", "Winter 2026"
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

        MvcResult searchResult = mockMvc.perform(get(
                        "/api/v1/cohorts/{cohortId}/enrollments?search={search}",
                        course.cohort().id(),
                        learnerUserId.toString().substring(0, 8)
                ).with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        CohortEnrollmentListResponse searchMatches = objectMapper.readValue(
                searchResult.getResponse().getContentAsString(),
                CohortEnrollmentListResponse.class
        );

        assertThat(searchMatches.enrollments())
                .extracting(CohortEnrollmentResponse::learnerUserId)
                .containsExactly(learnerUserId);
        assertThat(searchMatches.totalCount()).isEqualTo(1);
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

    private void setEnrollmentPolicy(UUID cohortId, String policy) {
        jdbcClient.sql("UPDATE cohorts SET enrollment_policy = :policy WHERE id = :cohortId")
                .param("policy", policy)
                .param("cohortId", cohortId)
                .update();
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

    private record SupportQuestionChannelAccessResponse(
            UUID channelId,
            UUID courseId,
            UUID studyServerId,
            String channelName,
            boolean canPostSupportQuestion,
            boolean canViewUnansweredSupportQuestions
    ) {
    }
}
