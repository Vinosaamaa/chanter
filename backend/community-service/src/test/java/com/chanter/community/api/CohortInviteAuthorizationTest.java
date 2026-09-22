package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.application.CourseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CohortInviteAuthorizationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcClient jdbc;
    @Autowired private CourseRepository repository;

    @Test
    void ownerWithoutInstructorRoleAndAssignedInstructorCanRetrieveTheSameInvite() throws Exception {
        CohortFixture fixture = createCohort();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM course_roles WHERE course_id=:course AND user_id=:owner")
                .param("course", fixture.course()).param("owner", fixture.owner()).query(Integer.class).single())
                .isZero();
        for (UUID actor : new UUID[] {fixture.owner(), fixture.instructor()}) {
            assertThat(repository.cohortHasPeopleManager(fixture.cohort(), actor)).isTrue();
            mvc.perform(get("/api/v1/cohorts/{cohort}/invite", fixture.cohort()).with(asUser(actor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("cohortId").value(fixture.cohort().toString()))
                    .andExpect(jsonPath("inviteCode").value(fixture.invite().toString()));
            assertThat(repository.findCohortInviteCodeForPeopleManager(fixture.cohort(), actor))
                    .contains(fixture.invite());
        }
    }

    @Test
    void inviteRemainsPrivateToThisCohortsPeopleManagers() throws Exception {
        CohortFixture fixture = createCohort();
        CohortFixture unrelated = createCohort();
        UUID learner = UUID.randomUUID();
        UUID ta = UUID.randomUUID();
        for (UUID enrolled : new UUID[] {learner, ta}) {
            mvc.perform(post("/api/v1/cohorts/{cohort}/enrollments", fixture.cohort())
                            .with(asUser(fixture.owner())).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("learnerUserId", enrolled))))
                    .andExpect(status().isCreated());
        }
        repository.addTeachingAssistant(fixture.cohort(), ta);
        for (UUID actor : new UUID[] {learner, ta, unrelated.owner(), unrelated.instructor(), UUID.randomUUID()}) {
            assertThat(repository.cohortHasPeopleManager(fixture.cohort(), actor)).isFalse();
            mvc.perform(get("/api/v1/cohorts/{cohort}/invite", fixture.cohort()).with(asUser(actor)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("inviteCode").doesNotExist());
            assertThat(repository.findCohortInviteCodeForPeopleManager(fixture.cohort(), actor)).isEmpty();
        }
    }

    @Test
    void missingCohortRemainsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        mvc.perform(get("/api/v1/cohorts/{cohort}/invite", missing).with(asUser(actor)))
                .andExpect(status().isNotFound());
        assertThat(repository.findCohortInviteCodeForPeopleManager(missing, actor)).isEmpty();
    }

    private CohortFixture createCohort() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID instructor = UUID.randomUUID();
        JsonNode server = mapper.readTree(mvc.perform(post("/api/v1/study-servers")
                        .with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Invite authorization"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode course = mapper.readTree(mvc.perform(post("/api/v1/study-servers/{server}/courses", server.get("id").asText())
                        .with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("title", "Separate instructor", "cohortName", "Autumn"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID courseId = UUID.fromString(course.get("id").asText());
        UUID cohortId = UUID.fromString(course.get("cohort").get("id").asText());
        jdbc.sql("UPDATE course_roles SET user_id=:instructor WHERE course_id=:course AND role='INSTRUCTOR'")
                .param("instructor", instructor).param("course", courseId).update();
        UUID invite = jdbc.sql("SELECT invite_code FROM cohorts WHERE id=:cohort")
                .param("cohort", cohortId).query(UUID.class).single();
        return new CohortFixture(owner, instructor, courseId, cohortId, invite);
    }

    private record CohortFixture(UUID owner, UUID instructor, UUID course, UUID cohort, UUID invite) {}
}
