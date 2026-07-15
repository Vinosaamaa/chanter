package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.infra.TestAuthUserDirectoryClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class HomeSummarySmokeTest {

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
    void homeSummaryIncludesOfficeHoursAttentionAndUpNextForAccessibleMember() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Dr. Ada");

        UUID studyServerId = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServerId, ownerUserId);
        Instant startsAt = Instant.now().plus(45, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);
        OfficeHoursSessionResponse session = scheduleSession(course.cohort().id(), ownerUserId, startsAt, endsAt);

        MvcResult result = mockMvc.perform(get("/api/v1/me/home-summary").with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partialFailures").isArray())
                .andExpect(jsonPath("$.partialFailures.length()").value(0))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].progress").value((Object) null))
                .andExpect(jsonPath("$.courses[0].progressUnavailableReason").value("NO_CURRICULUM"))
                .andExpect(jsonPath("$.courses[0].instructorDisplayName").value("Dr. Ada"))
                .andExpect(jsonPath("$.attention.length()").value(1))
                .andExpect(jsonPath("$.attention[0].kind").value("OFFICE_HOURS"))
                .andExpect(jsonPath("$.upNext.length()").value(1))
                .andExpect(jsonPath("$.upNext[0].kind").value("OFFICE_HOURS"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String expectedHref = "/app/servers/" + studyServerId
                + "/courses/" + course.id()
                + "/office-hours?cohort=" + course.cohort().id()
                + "&session=" + session.id();
        assertThat(body.get("attention").get(0).get("href").asText()).isEqualTo(expectedHref);
        assertThat(body.get("upNext").get(0).get("href").asText()).isEqualTo(expectedHref);
        assertThat(body.get("courses").get(0).get("href").asText())
                .isEqualTo("/app/servers/" + studyServerId
                        + "/courses/" + course.id()
                        + "/overview?cohort=" + course.cohort().id());
    }

    @Test
    void outsiderSeesEmptyHomeSummary() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Owner");
        createCourse(createStudyServer(ownerUserId), ownerUserId);

        mockMvc.perform(get("/api/v1/me/home-summary").with(asUser(outsiderUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(0))
                .andExpect(jsonPath("$.attention.length()").value(0))
                .andExpect(jsonPath("$.upNext.length()").value(0))
                .andExpect(jsonPath("$.partialFailures.length()").value(0));
    }

    private UUID createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Home Summary Hub"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private CourseResponse createCourse(UUID studyServerId, UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "CS 101 — Intro",
                                "cohortName", "Fall 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CourseResponse.class);
    }

    private OfficeHoursSessionResponse scheduleSession(
            UUID cohortId,
            UUID instructorUserId,
            Instant startsAt,
            Instant endsAt
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/office-hours", cohortId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), OfficeHoursSessionResponse.class);
    }

    private record CourseResponse(UUID id, CohortResponse cohort) {
    }

    private record CohortResponse(UUID id, String name) {
    }

    private record OfficeHoursSessionResponse(UUID id, UUID cohortId) {
    }
}
