package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    void ownerCreatesInviteOnlyCohortAndOnlyExactInviteAdmitsAnOutsider() throws Exception {
        UUID owner = UUID.randomUUID();
        StudyServerResponse server = createStudyServer(owner);
        CourseResponse course = createCourse(server.id(), owner, "Invited course", "First cohort", "INVITE_ONLY");
        UUID cohortId = course.cohort().id();
        UUID invite = getInviteCode(cohortId, owner);
        assertThat(jdbcClient.sql("SELECT enrollment_policy FROM cohorts WHERE id = :id")
                .param("id", cohortId).query(String.class).single()).isEqualTo("INVITE_ONLY");
        for (Map<String, Object> body : java.util.List.of(Map.<String, Object>of(),
                Map.<String, Object>of("inviteCode", UUID.randomUUID()))) {
            mockMvc.perform(post("/api/v1/cohorts/{id}/join", cohortId).with(asUser(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/cohorts/{id}/join", cohortId).with(asUser(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteCode", invite))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/study-servers/{id}/course-catalog", server.id()).with(asUser(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].cohorts[0].enrollmentPolicy").value("INVITE_ONLY"))
                .andExpect(jsonPath("$.courses[0].cohorts[0].enrolled").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "OPENING_SOON"})
    void creationDoesNotBypassUnavailableCohortGates(String policy) throws Exception {
        UUID owner = UUID.randomUUID();
        StudyServerResponse server = createStudyServer(owner);
        CourseResponse course = createCourse(server.id(), owner, "Unavailable course", "First cohort", policy);
        UUID invite = getInviteCode(course.cohort().id(), owner);
        mockMvc.perform(post("/api/v1/cohorts/{id}/join", course.cohort().id()).with(asUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteCode", invite))))
                .andExpect(status().isConflict());
    }

    @Test
    void creationValidatesPolicyAndKeepsOwnerAuthorityAndDraftCompatibility() throws Exception {
        UUID owner = UUID.randomUUID();
        StudyServerResponse server = createStudyServer(owner);
        for (Object invalid : java.util.List.of("PUBLIC", "invite_only", "", 1, true)) {
            mockMvc.perform(post("/api/v1/study-servers/{id}/courses", server.id()).with(asUser(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                    Map.of("title", "Invalid", "cohortName", "Cohort", "enrollmentPolicy", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/study-servers/{id}/courses", server.id()).with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Draft\",\"enrollmentPolicy\":\"INVITE_ONLY\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM courses WHERE study_server_id = :id")
                .param("id", server.id()).query(Long.class).single()).isZero();
        mockMvc.perform(post("/api/v1/study-servers/{id}/courses", server.id()).with(asUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unauthorized\",\"cohortName\":\"Cohort\",\"enrollmentPolicy\":\"INVITE_ONLY\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/study-servers/{id}/courses", server.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Unauthorized\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/study-servers/{id}/courses", server.id()).with(asUser(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Draft\"}"))
                .andExpect(status().isCreated());
        CourseResponse explicitNull = createCourse(server.id(), owner, "Default policy", "Cohort", (String) null);
        assertThat(jdbcClient.sql("SELECT enrollment_policy FROM cohorts WHERE id = :id")
                .param("id", explicitNull.cohort().id()).query(String.class).single()).isEqualTo("OPEN");
    }

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
    void outsiderCannotUseAnOpenCohortInviteAsStudyServerMembership() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);
        CourseResponse openCourse = createCourse(
                studyServer.id(),
                ownerUserId,
                "MATH 201 - Linear Algebra",
                "Fall cohort"
        );
        UUID inviteCode = getInviteCode(openCourse.cohort().id(), ownerUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/join", openCourse.cohort().id())
                        .with(asUser(outsiderUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteCode", inviteCode))))
                .andExpect(status().isForbidden());
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
        return createCourse(studyServerId, ownerUserId, title, cohortName, Map.of());
    }

    private CourseResponse createCourse(UUID studyServerId, UUID ownerUserId, String title,
            String cohortName, String policy) throws Exception {
        Map<String, Object> choice = new HashMap<>();
        choice.put("enrollmentPolicy", policy);
        return createCourse(studyServerId, ownerUserId, title, cohortName, choice);
    }

    private CourseResponse createCourse(UUID studyServerId, UUID ownerUserId, String title,
            String cohortName, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("title", title);
        body.put("cohortName", cohortName);
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/study-servers/{studyServerId}/courses",
                        studyServerId
                )
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
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
