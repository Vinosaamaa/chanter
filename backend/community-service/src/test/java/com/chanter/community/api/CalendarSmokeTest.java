package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CalendarSmokeTest {

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
    void calendarAggregatesOfficeHoursAndEventsForAccessibleMember() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Dr. Ada");

        UUID studyServerId = createStudyServer(ownerUserId, "Calendar Hub");
        CourseResponse course = createCourse(studyServerId, ownerUserId);

        Instant ohStarts = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant ohEnds = ohStarts.plus(1, ChronoUnit.HOURS);
        OfficeHoursSessionResponse session = scheduleSession(course.cohort().id(), ownerUserId, ohStarts, ohEnds);

        Instant eventStarts = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant eventEnds = eventStarts.plus(90, ChronoUnit.MINUTES);
        UUID eventId = createEvent(studyServerId, ownerUserId, "Hackathon kickoff", eventStarts, eventEnds);

        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "GOING"))))
                .andExpect(status().isOk());

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(14, ChronoUnit.DAYS);

        MvcResult result = mockMvc.perform(get("/api/v1/me/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.notes.length()").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("notes").get(0).asText()).contains("Deadlines are omitted");

        JsonNode ohItem = findByType(body, "OFFICE_HOURS");
        assertThat(ohItem.get("title").asText()).isEqualTo("Office hours");
        assertThat(ohItem.get("href").asText()).isEqualTo(
                "/app/servers/" + studyServerId
                        + "/courses/" + course.id()
                        + "/office-hours?cohort=" + course.cohort().id()
                        + "&session=" + session.id()
        );
        assertThat(ohItem.get("actionKind").asText()).isEqualTo("JOIN");

        JsonNode eventItem = findByType(body, "EVENT");
        assertThat(eventItem.get("title").asText()).isEqualTo("Hackathon kickoff");
        assertThat(eventItem.get("viewerRsvp").asText()).isEqualTo("GOING");
        assertThat(eventItem.get("href").asText()).isEqualTo(
                "/app/servers/" + studyServerId + "/community/events?event=" + eventId
        );
        assertThat(eventItem.get("actionKind").asText()).isEqualTo("RSVP");
    }

    @Test
    void calendarSupportsTypeFilterSearchAndGoing() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Owner");
        UUID studyServerId = createStudyServer(ownerUserId, "Filter Hub");
        CourseResponse course = createCourse(studyServerId, ownerUserId);

        Instant ohStarts = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        scheduleSession(course.cohort().id(), ownerUserId, ohStarts, ohStarts.plus(1, ChronoUnit.HOURS));

        Instant eventStarts = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID eventId = createEvent(studyServerId, ownerUserId, "AI guest talk", eventStarts, eventStarts.plus(1, ChronoUnit.HOURS));
        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "GOING"))))
                .andExpect(status().isOk());

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(10, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/me/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("types", "OFFICE_HOURS")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("OFFICE_HOURS"));

        mockMvc.perform(get("/api/v1/me/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("types", "GOING")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("EVENT"))
                .andExpect(jsonPath("$.items[0].viewerRsvp").value("GOING"));

        mockMvc.perform(get("/api/v1/me/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("search", "guest")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("AI guest talk"));

        mockMvc.perform(get("/api/v1/me/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("types", "DEADLINE")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.notes.length()").value(1));
    }

    @Test
    void outsiderSeesEmptyCalendar() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@hub.example", "Owner");
        UUID studyServerId = createStudyServer(ownerUserId, "Private Hub");
        createCourse(studyServerId, ownerUserId);
        Instant starts = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        createEvent(studyServerId, ownerUserId, "Secret event", starts, starts.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/me/calendar")
                        .param("from", Instant.now().toString())
                        .param("to", Instant.now().plus(7, ChronoUnit.DAYS).toString())
                        .with(asUser(outsiderUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private static JsonNode findByType(JsonNode body, String type) {
        for (JsonNode item : body.get("items")) {
            if (type.equals(item.get("type").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing calendar item type: " + type);
    }

    private UUID createStudyServer(UUID ownerUserId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
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

    private UUID createEvent(
            UUID studyServerId,
            UUID ownerUserId,
            String title,
            Instant startsAt,
            Instant endsAt
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers/{id}/events", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString(),
                                "visibility", "HUB"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private record CourseResponse(UUID id, CohortResponse cohort) {
    }

    private record CohortResponse(UUID id, String name) {
    }

    private record OfficeHoursSessionResponse(UUID id, UUID cohortId) {
    }
}
