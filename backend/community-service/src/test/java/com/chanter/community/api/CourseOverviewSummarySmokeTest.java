package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class CourseOverviewSummarySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void overviewSummaryIncludesCohortScopedOfficeHoursAndNullProgress() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID studyServerId = createStudyServer(ownerUserId);
        CourseResponse course = createCourse(studyServerId, ownerUserId);
        Instant startsAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);
        OfficeHoursSessionResponse session = scheduleSession(course.cohort().id(), ownerUserId, startsAt, endsAt);

        MvcResult result = mockMvc.perform(get("/api/v1/courses/{courseId}/overview-summary", course.id())
                        .param("cohortId", course.cohort().id().toString())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value((Object) null))
                .andExpect(jsonPath("$.progressUnavailableReason").value("NO_CURRICULUM"))
                .andExpect(jsonPath("$.recentActivity.length()").value(0))
                .andExpect(jsonPath("$.partialFailures.length()").value(0))
                .andExpect(jsonPath("$.thisWeek.length()").value(1))
                .andExpect(jsonPath("$.thisWeek[0].kind").value("OFFICE_HOURS"))
                .andExpect(jsonPath("$.upNext.length()").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String expectedOhHref = "/app/servers/" + studyServerId
                + "/courses/" + course.id()
                + "/office-hours?cohort=" + course.cohort().id()
                + "&session=" + session.id();
        assertThat(body.get("thisWeek").get(0).get("href").asText()).isEqualTo(expectedOhHref);

        JsonNode studyRoom = null;
        for (JsonNode item : body.get("upNext")) {
            if ("STUDY_ROOM".equals(item.get("kind").asText())) {
                studyRoom = item;
            }
        }
        assertThat(studyRoom).isNotNull();
        assertThat(studyRoom.get("href").asText()).contains("/chat?cohort=" + course.cohort().id());
        assertThat(studyRoom.get("href").asText()).contains("channel=");
    }

    @Test
    void outsiderCannotReadOverviewSummary() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        CourseResponse course = createCourse(createStudyServer(ownerUserId), ownerUserId);

        mockMvc.perform(get("/api/v1/courses/{courseId}/overview-summary", course.id())
                        .param("cohortId", course.cohort().id().toString())
                        .with(asUser(outsiderUserId)))
                .andExpect(status().isForbidden());
    }

    private UUID createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Overview Summary Hub"
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

    private record OfficeHoursSessionResponse(UUID id) {
    }
}
