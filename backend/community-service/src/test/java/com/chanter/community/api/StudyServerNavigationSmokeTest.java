package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class StudyServerNavigationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void instructorOnlyReceivesCapabilitiesForTheirCourse() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse instructedCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "Instructor Course",
                "Summer 2026"
        );
        CourseResponse unrelatedCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "Owner Only Course",
                "Fall 2026"
        );

        jdbcClient.sql("""
                        INSERT INTO course_roles (course_id, user_id, role)
                        VALUES (:courseId, :userId, 'INSTRUCTOR')
                        """)
                .param("courseId", instructedCourse.id())
                .param("userId", instructorUserId)
                .update();

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
                        studyServer.id()
                ).with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canViewFullCatalog").value(false))
                .andExpect(jsonPath("$.capabilities.owner").value(false))
                .andExpect(jsonPath("$.capabilities.canTeach").value(true))
                .andExpect(jsonPath("$.capabilities.canCreateCourse").value(false))
                .andExpect(jsonPath("$.capabilities.canManageBilling").value(false))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].id").value(instructedCourse.id().toString()))
                .andExpect(jsonPath("$.courses[0].capabilities.instructor").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.teachingAssistant").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.enrolled").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageCourse").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageQuestions").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canApproveFaq").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageTaQueue").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canUploadResources").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canScheduleOfficeHours").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canManagePeople").value(true))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(unrelatedCourse.id().toString()));
    }

    @Test
    void teachingAssistantOnlyReceivesQuestionSupportCapabilitiesForAssignedCohort() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID teachingAssistantUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse supportedCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "Supported Course",
                "Summer 2026"
        );
        CourseResponse unrelatedCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "Unrelated Course",
                "Fall 2026"
        );

        jdbcClient.sql("""
                        INSERT INTO cohort_roles (cohort_id, user_id, role)
                        VALUES (:cohortId, :userId, 'TA')
                        """)
                .param("cohortId", supportedCourse.cohort().id())
                .param("userId", teachingAssistantUserId)
                .update();

        CourseChannelResponse questionsChannel = supportedCourse.channels().stream()
                .filter(channel -> channel.name().equals("questions"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
                        studyServer.id()
                ).with(asUser(teachingAssistantUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.owner").value(false))
                .andExpect(jsonPath("$.capabilities.canTeach").value(false))
                .andExpect(jsonPath("$.capabilities.canCreateCourse").value(false))
                .andExpect(jsonPath("$.capabilities.canManageBilling").value(false))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].id").value(supportedCourse.id().toString()))
                .andExpect(jsonPath("$.courses[0].capabilities.instructor").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.teachingAssistant").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.enrolled").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageCourse").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageQuestions").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canApproveFaq").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageTaQueue").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canUploadResources").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canScheduleOfficeHours").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canManagePeople").value(false))
                .andExpect(jsonPath("$.courses[0].cohorts.length()").value(1))
                .andExpect(jsonPath("$.courses[0].cohorts[0].id")
                        .value(supportedCourse.cohort().id().toString()))
                .andExpect(jsonPath("$.courses[0].cohorts[0].capabilities.teachingAssistant").value(true))
                .andExpect(jsonPath("$.courses[0].cohorts[0].capabilities.canManage").value(true))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(unrelatedCourse.id().toString()));

        mockMvc.perform(get(
                        "/api/v1/course-channels/{channelId}/support-question-access",
                        questionsChannel.id()
                ).with(asUser(teachingAssistantUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canPostSupportQuestion").value(false))
                .andExpect(jsonPath("$.canViewUnansweredSupportQuestions").value(true));

        mockMvc.perform(get(
                        "/api/v1/cohorts/{cohortId}/ta-queue-access",
                        supportedCourse.cohort().id()
                ).with(asUser(teachingAssistantUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canAddToTaQueue").value(false))
                .andExpect(jsonPath("$.canManageTaQueue").value(true));

        mockMvc.perform(get(
                        "/api/v1/courses/{courseId}/resource-access",
                        supportedCourse.id()
                ).with(asUser(teachingAssistantUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUploadCourseResource").value(false))
                .andExpect(jsonPath("$.canViewCourseResources").value(true));

        mockMvc.perform(get(
                        "/api/v1/cohorts/{cohortId}/office-hours-access",
                        supportedCourse.cohort().id()
                ).with(asUser(teachingAssistantUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canScheduleOfficeHours").value(false))
                .andExpect(jsonPath("$.canJoinOfficeHours").value(true))
                .andExpect(jsonPath("$.canManageOfficeHours").value(false));
    }

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

        UUID inaccessibleCohortId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO cohorts (id, course_id, name, invite_code)
                        VALUES (:id, :courseId, 'Winter 2027', :inviteCode)
                        """)
                .param("id", inaccessibleCohortId)
                .param("courseId", course.id())
                .param("inviteCode", UUID.randomUUID())
                .update();

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
                .andExpect(jsonPath("$.capabilities.owner").value(true))
                .andExpect(jsonPath("$.capabilities.canTeach").value(true))
                .andExpect(jsonPath("$.capabilities.canCreateCourse").value(true))
                .andExpect(jsonPath("$.capabilities.canManageCommunity").value(true))
                .andExpect(jsonPath("$.capabilities.canManageEvents").value(true))
                .andExpect(jsonPath("$.capabilities.canManageBilling").value(true))
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
                .andExpect(jsonPath("$.capabilities.owner").value(false))
                .andExpect(jsonPath("$.capabilities.canTeach").value(false))
                .andExpect(jsonPath("$.capabilities.canCreateCourse").value(false))
                .andExpect(jsonPath("$.capabilities.canManageCommunity").value(false))
                .andExpect(jsonPath("$.capabilities.canManageEvents").value(false))
                .andExpect(jsonPath("$.capabilities.canManageBilling").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.instructor").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.teachingAssistant").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.enrolled").value(true))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageCourse").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canApproveFaq").value(false))
                .andExpect(jsonPath("$.courses[0].capabilities.canManageTaQueue").value(false))
                .andExpect(jsonPath("$.courses[0].cohorts.length()").value(1))
                .andExpect(jsonPath("$.courses[0].cohorts[0].id").value(course.cohort().id().toString()))
                .andExpect(jsonPath("$.courses[0].cohorts[0].capabilities.enrolled").value(true))
                .andReturn();
        StudyServerNavigationResponse learnerNavigation = objectMapper.readValue(
                learnerNavigationResult.getResponse().getContentAsString(),
                StudyServerNavigationResponse.class
        );

        assertThat(learnerNavigation.studyServerChannels())
                .extracting(StudyAssistantGrantCandidatesResponse.ChannelResponse::name)
                .contains("announcements", "general");
        UUID generalChannelId = learnerNavigation.studyServerChannels().stream()
                .filter(channel -> channel.name().equals("general"))
                .map(StudyAssistantGrantCandidatesResponse.ChannelResponse::id)
                .findFirst()
                .orElseThrow();
        mockMvc.perform(get(
                        "/api/v1/study-server-channels/{channelId}/channel-message-access",
                        generalChannelId
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReadMessages").value(true))
                .andExpect(jsonPath("$.canPostMessages").value(true));
        assertThat(learnerNavigation.courses()).hasSize(1);
        assertThat(learnerNavigation.courses().getFirst().id()).isEqualTo(course.id());
        assertThat(learnerNavigation.courses().getFirst().channels()).hasSize(3);
        assertThat(learnerNavigation.courses().getFirst().cohorts())
                .extracting(StudyServerNavigationResponse.CohortResponse::id)
                .containsExactly(course.cohort().id())
                .doesNotContain(inaccessibleCohortId);
        assertThat(learnerNavigation.courses().stream().map(StudyServerNavigationResponse.CourseResponse::id))
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

    private CourseResponse createCourse(
            UUID studyServerId,
            UUID ownerUserId,
            String title,
            String cohortName
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "cohortName", cohortName
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), CourseResponse.class);
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

    private record CourseChannelResponse(UUID id, String name) {
    }
}
