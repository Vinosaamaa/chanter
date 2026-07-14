package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class CourseDiscoverySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void returnsRealPublishedCoursesWithBackendSearchAndExactEnrollmentState() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse enrolledCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "CS 101 - Intro to CS",
                "Spring cohort"
        );
        CourseResponse discoverableCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "MATH 201 - Linear Algebra",
                "Fall cohort"
        );
        enrollLearner(enrolledCourse.cohort().id(), ownerUserId, learnerUserId);

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/course-catalog",
                        studyServer.id()
                )
                        .queryParam("search", "linear")
                        .queryParam("filter", "ALL")
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].id").value(discoverableCourse.id().toString()))
                .andExpect(jsonPath("$.courses[0].title").value("MATH 201 - Linear Algebra"))
                .andExpect(jsonPath("$.courses[0].instructorUserId").value(ownerUserId.toString()))
                .andExpect(jsonPath("$.courses[0].cohorts.length()").value(1))
                .andExpect(jsonPath("$.courses[0].cohorts[0].id")
                        .value(discoverableCourse.cohort().id().toString()))
                .andExpect(jsonPath("$.courses[0].cohorts[0].name").value("Fall cohort"))
                .andExpect(jsonPath("$.courses[0].cohorts[0].enrollmentPolicy").value("OPEN"))
                .andExpect(jsonPath("$.courses[0].cohorts[0].enrolled").value(false))
                .andExpect(jsonPath("$.courses[0].cohorts[0].learnerCount").value(0));
    }

    @Test
    void learnerJoinsAnOpenCohortWithoutAnInviteCode() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse membershipCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "CS 101 - Intro to CS",
                "Spring cohort"
        );
        CourseResponse openCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "MATH 201 - Linear Algebra",
                "Fall cohort"
        );
        enrollLearner(membershipCourse.cohort().id(), ownerUserId, learnerUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", openCourse.cohort().id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/course-catalog",
                        studyServer.id()
                )
                        .queryParam("search", "linear")
                        .queryParam("filter", "ENROLLED")
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].id").value(openCourse.id().toString()))
                .andExpect(jsonPath("$.courses[0].cohorts[0].enrolled").value(true))
                .andExpect(jsonPath("$.courses[0].cohorts[0].learnerCount").value(1));
    }

    @Test
    void enforcesInviteAvailabilityPublicationAndMembershipBoundaries() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse membershipCourse = createCourse(
                studyServer.id(), ownerUserId, "CS 101 - Intro to CS", "Spring cohort"
        );
        CourseResponse inviteCourse = createCourse(
                studyServer.id(), ownerUserId, "MATH 201 - Linear Algebra", "Fall cohort"
        );
        CourseResponse openingSoonCourse = createCourse(
                studyServer.id(), ownerUserId, "ECON 210 - Microeconomics", "Winter cohort"
        );
        CourseResponse hiddenCourse = createCourse(
                studyServer.id(), ownerUserId, "Hidden Draft", "Private cohort"
        );
        enrollLearner(membershipCourse.cohort().id(), ownerUserId, learnerUserId);
        setEnrollmentPolicy(inviteCourse.cohort().id(), "INVITE_ONLY");
        setEnrollmentPolicy(openingSoonCourse.cohort().id(), "OPENING_SOON");
        jdbcClient.sql("UPDATE courses SET published = FALSE WHERE id = :courseId")
                .param("courseId", hiddenCourse.id())
                .update();

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", inviteCourse.cohort().id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        UUID inviteCode = getInviteCode(inviteCourse.cohort().id(), ownerUserId);
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", inviteCourse.cohort().id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteCode", inviteCode))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", openingSoonCourse.cohort().id())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/course-catalog",
                        studyServer.id()
                )
                        .queryParam("filter", "OPENING_SOON")
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].id").value(openingSoonCourse.id().toString()));

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/course-catalog",
                        studyServer.id()
                )
                        .queryParam("search", "hidden")
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isEmpty());

        mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/course-catalog",
                        studyServer.id()
                ).with(asUser(outsiderUserId)))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Spring Bootcamp Hub"))))
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
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/study-servers/{studyServerId}/courses",
                        studyServerId
                )
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

    private void enrollLearner(UUID cohortId, UUID ownerUserId, UUID learnerUserId) throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", cohortId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserId", learnerUserId.toString()
                        ))))
                .andExpect(status().isCreated());
    }

    private UUID getInviteCode(UUID cohortId, UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/cohorts/{cohortId}/invite", cohortId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CohortInviteResponse.class
        ).inviteCode();
    }

    private void setEnrollmentPolicy(UUID cohortId, String policy) {
        jdbcClient.sql("UPDATE cohorts SET enrollment_policy = :policy WHERE id = :cohortId")
                .param("policy", policy)
                .param("cohortId", cohortId)
                .update();
    }
}
