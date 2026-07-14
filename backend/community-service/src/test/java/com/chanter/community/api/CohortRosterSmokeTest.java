package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.infra.TestAuthUserDirectoryClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
class CohortRosterSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAuthUserDirectoryClient userDirectory;

    @BeforeEach
    void resetDirectory() {
        userDirectory.reset();
    }

    @Test
    void instructorEnrollsRegisteredLearnerByEmailAndRosterUsesRealProfiles() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "alex@spring.example", "Dr. Alex Johnson");
        userDirectory.register(learnerUserId, "sam@spring.example", "Sam Chen");

        CourseFixture course = createCourse(instructorUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", " SAM@spring.example "
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.userId").value(instructorUserId.toString()))
                .andExpect(jsonPath("$.instructor.displayName").value("Dr. Alex Johnson"))
                .andExpect(jsonPath("$.learners.length()").value(1))
                .andExpect(jsonPath("$.learners[0].userId").value(learnerUserId.toString()))
                .andExpect(jsonPath("$.learners[0].displayName").value("Sam Chen"))
                .andExpect(jsonPath("$.learners[0].status").value("ENROLLED"));
    }

    @Test
    void enrollmentRequiresExactlyOneLearnerIdentity() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID emailUserId = UUID.randomUUID();
        UUID legacyUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "identity-instructor@spring.example", "Identity Instructor");
        userDirectory.register(emailUserId, "identity-email@spring.example", "Email Learner");
        userDirectory.register(legacyUserId, "identity-legacy@spring.example", "Legacy Learner");
        CourseFixture course = createCourse(instructorUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "identity-email@spring.example",
                                "learnerUserId", legacyUserId
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerCount").value(0));
    }

    @Test
    void instructorPromotesEnrolledLearnerToTeachingAssistant() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "instructor-ta@spring.example", "Dr. Instructor");
        userDirectory.register(learnerUserId, "future-ta@spring.example", "Maria Gonzalez");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "future-ta@spring.example");

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/teaching-assistants/{userId}",
                        course.cohortId(), learnerUserId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingAssistants.length()").value(1))
                .andExpect(jsonPath("$.teachingAssistants[0].userId").value(learnerUserId.toString()))
                .andExpect(jsonPath("$.teachingAssistants[0].displayName").value("Maria Gonzalez"))
                .andExpect(jsonPath("$.teachingAssistants[0].role").value("TA"))
                .andExpect(jsonPath("$.learners.length()").value(0));
    }

    @Test
    void enrolledLearnerCanViewRealCohortRoster() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "roster-instructor@spring.example", "Roster Instructor");
        userDirectory.register(learnerUserId, "roster-viewer@spring.example", "Roster Viewer");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "roster-viewer@spring.example");

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructor.displayName").value("Roster Instructor"))
                .andExpect(jsonPath("$.learners[0].displayName").value("Roster Viewer"));
    }

    @Test
    void instructorBulkAssignsTeachingAssistantAndAssignmentsPersist() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID teachingAssistantUserId = UUID.randomUUID();
        UUID firstLearnerUserId = UUID.randomUUID();
        UUID secondLearnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "assign-instructor@spring.example", "Assign Instructor");
        userDirectory.register(teachingAssistantUserId, "assigned-ta@spring.example", "Jordan Kim");
        userDirectory.register(firstLearnerUserId, "first-assignment@spring.example", "First Learner");
        userDirectory.register(secondLearnerUserId, "second-assignment@spring.example", "Second Learner");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "assigned-ta@spring.example");
        enrollByEmail(course.cohortId(), instructorUserId, "first-assignment@spring.example");
        enrollByEmail(course.cohortId(), instructorUserId, "second-assignment@spring.example");
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/teaching-assistants/{userId}",
                        course.cohortId(), teachingAssistantUserId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/enrollments/teaching-assistant", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserIds", List.of(firstLearnerUserId, secondLearnerUserId),
                                "teachingAssistantUserId", teachingAssistantUserId
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learners.length()").value(2))
                .andExpect(jsonPath("$.learners[0].assignedTeachingAssistantUserId")
                        .value(teachingAssistantUserId.toString()))
                .andExpect(jsonPath("$.learners[1].assignedTeachingAssistantUserId")
                        .value(teachingAssistantUserId.toString()));
    }

    @Test
    void removingTeachingAssistantRoleClearsLearnerAssignments() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID teachingAssistantUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "remove-instructor@spring.example", "Remove Instructor");
        userDirectory.register(teachingAssistantUserId, "remove-ta@spring.example", "Former TA");
        userDirectory.register(learnerUserId, "remove-learner@spring.example", "Assigned Learner");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "remove-ta@spring.example");
        enrollByEmail(course.cohortId(), instructorUserId, "remove-learner@spring.example");
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/teaching-assistants/{userId}",
                        course.cohortId(), teachingAssistantUserId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/enrollments/teaching-assistant", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "learnerUserIds", List.of(learnerUserId),
                                "teachingAssistantUserId", teachingAssistantUserId
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/cohorts/{cohortId}/teaching-assistants/{userId}",
                        course.cohortId(), teachingAssistantUserId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingAssistants.length()").value(0))
                .andExpect(jsonPath("$.learners[?(@.userId == '%s')].assignedTeachingAssistantUserId"
                        .formatted(learnerUserId)).value((Object) null));
    }

    @Test
    void instructorCreatesAndCancelsPendingInvitationForVerifiedAccount() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        UUID enrolledLearnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "invite-instructor@spring.example", "Invite Instructor");
        userDirectory.register(invitedUserId, "invited@spring.example", "Pending Learner");
        userDirectory.register(enrolledLearnerUserId, "enrolled@spring.example", "Enrolled Learner");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "enrolled@spring.example");

        MvcResult invitationResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/invitations", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", " INVITED@spring.example "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(invitedUserId.toString()))
                .andExpect(jsonPath("$.displayName").value("Pending Learner"))
                .andExpect(jsonPath("$.email").value("invited@spring.example"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        UUID invitationId = UUID.fromString(
                objectMapper.readTree(invitationResult.getResponse().getContentAsString()).get("id").asText()
        );

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.learners[0].invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.learners[0].status").value("PENDING"))
                .andExpect(jsonPath("$.learners[0].email").value("invited@spring.example"));

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(enrolledLearnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.learners.length()").value(1));

        mockMvc.perform(delete("/api/v1/cohorts/{cohortId}/invitations/{invitationId}",
                        course.cohortId(), invitationId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.learners.length()").value(1));
    }

    @Test
    void enrollingInvitedAccountResolvesPendingInvitation() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "accept-instructor@spring.example", "Accept Instructor");
        userDirectory.register(invitedUserId, "accepted@spring.example", "Accepted Learner");
        CourseFixture course = createCourse(instructorUserId);
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/invitations", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "accepted@spring.example"))))
                .andExpect(status().isCreated());

        enrollByEmail(course.cohortId(), instructorUserId, "accepted@spring.example");

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.learnerCount").value(1))
                .andExpect(jsonPath("$.learners.length()").value(1))
                .andExpect(jsonPath("$.learners[0].userId").value(invitedUserId.toString()))
                .andExpect(jsonPath("$.learners[0].status").value("ENROLLED"));
    }

    @Test
    void instructorRemovesEnrollmentAndLearnerLosesRosterAccess() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "unenroll-instructor@spring.example", "Unenroll Instructor");
        userDirectory.register(learnerUserId, "unenrolled@spring.example", "Removed Learner");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "unenrolled@spring.example");

        mockMvc.perform(delete("/api/v1/cohorts/{cohortId}/enrollments/{learnerUserId}",
                        course.cohortId(), learnerUserId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerCount").value(0))
                .andExpect(jsonPath("$.learners.length()").value(0));
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void learnerCanReadRosterButCannotManagePeople() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        UUID candidateUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "secure-instructor@spring.example", "Secure Instructor");
        userDirectory.register(learnerUserId, "secure-learner@spring.example", "Secure Learner");
        userDirectory.register(candidateUserId, "secure-candidate@spring.example", "Secure Candidate");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "secure-learner@spring.example");

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/roster", course.cohortId())
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/invitations", course.cohortId())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "secure-candidate@spring.example"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/teaching-assistants/{userId}",
                        course.cohortId(), candidateUserId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/cohorts/{cohortId}/enrollments/{learnerUserId}",
                        course.cohortId(), learnerUserId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthorizedEnrollmentCannotProbeRegisteredEmails() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID unauthorizedUserId = UUID.randomUUID();
        UUID registeredUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "privacy-instructor@spring.example", "Privacy Instructor");
        userDirectory.register(registeredUserId, "private-account@spring.example", "Private Account");
        CourseFixture course = createCourse(instructorUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohortId())
                        .with(asUser(unauthorizedUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "private-account@spring.example"
                        ))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", course.cohortId())
                        .with(asUser(unauthorizedUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "missing-account@spring.example"
                        ))))
                .andExpect(status().isForbidden());
    }

    private void enrollByEmail(UUID cohortId, UUID instructorUserId, String email) throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", cohortId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());
    }

    private CourseFixture createCourse(UUID instructorUserId) throws Exception {
        MvcResult serverResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Spring Bootcamp Hub"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID studyServerId = UUID.fromString(
                objectMapper.readTree(serverResult.getResponse().getContentAsString()).get("id").asText()
        );

        MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServerId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "CS 101: Intro to Computer Science",
                                "cohortName", "Spring 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode course = objectMapper.readTree(courseResult.getResponse().getContentAsString());
        return new CourseFixture(
                UUID.fromString(course.get("id").asText()),
                UUID.fromString(course.get("cohort").get("id").asText())
        );
    }

    private record CourseFixture(UUID courseId, UUID cohortId) {
    }
}
