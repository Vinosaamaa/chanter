package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityEventSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void scopedFanoutSelectsOnlyAuthorizedRecipientsWithoutTheOldTwoHundredLimit() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID server = createStudyServer(owner);
        UUID course = UUID.randomUUID();
        UUID cohort = UUID.randomUUID();
        UUID otherCohort = UUID.randomUUID();
        UUID instructor = UUID.randomUUID();
        UUID otherLearner = UUID.randomUUID();
        jdbc.update("INSERT INTO courses(id,study_server_id,title,instructor_user_id,created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                course, server, "Scoped course", instructor);
        jdbc.update("INSERT INTO course_roles(course_id,user_id,role) VALUES (?,?,'INSTRUCTOR')", course, instructor);
        jdbc.update("INSERT INTO cohorts(id,course_id,name,invite_code) VALUES (?,?,?,?)", cohort, course, "Target", UUID.randomUUID());
        jdbc.update("INSERT INTO cohorts(id,course_id,name,invite_code) VALUES (?,?,?,?)", otherCohort, course, "Other", UUID.randomUUID());
        jdbc.update("INSERT INTO cohort_enrollments(cohort_id,learner_user_id,enrolled_by_user_id,enrolled_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", otherCohort, otherLearner, owner);
        for (int index = 0; index < 201; index++) {
            jdbc.update("INSERT INTO cohort_enrollments(cohort_id,learner_user_id,enrolled_by_user_id,enrolled_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", cohort, UUID.randomUUID(), owner);
        }
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        for (String scope : java.util.List.of("COHORT", "COURSE")) {
            Map<String, Object> request = new java.util.HashMap<>(Map.of(
                    "title", "Scoped event", "startsAt", start.toString(), "endsAt", start.plus(1, ChronoUnit.HOURS).toString(),
                    "visibility", scope, "courseId", course));
            if (scope.equals("COHORT")) request.put("cohortId", cohort);
            var result = mockMvc.perform(post("/api/v1/study-servers/{id}/events", server).with(asUser(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated()).andReturn();
            String eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
            var payloads = jdbc.queryForList("SELECT payload FROM durable_outbox WHERE destination='notification' AND aggregate_key LIKE ?",
                    String.class, "%:COMMUNITY_EVENT:" + eventId + ":COMMUNITY_EVENT");
            org.assertj.core.api.Assertions.assertThat(payloads).hasSize(scope.equals("COHORT") ? 201 : 203);
            if (scope.equals("COHORT")) {
                org.assertj.core.api.Assertions.assertThat(payloads).noneMatch(payload -> payload.contains(otherLearner.toString())
                        || payload.contains(instructor.toString()));
            }
        }
    }

    @Test
    void durableEventsSupportCreateRsvpFilterShareAndIcs() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        UUID studyServerId = createStudyServer(ownerUserId);

        Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(90, ChronoUnit.MINUTES);

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers/{id}/events", studyServerId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Guest talk: AI in industry",
                                "description", "Industry speakers share AI practice",
                                "location", "Auditorium",
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString(),
                                "visibility", "HUB"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goingCount").value(0))
                .andReturn();

        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        UUID eventId = UUID.fromString(created.get("id").asText());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?", Integer.class, "EVENT:" + eventId)).isEqualTo(1);
        String sharePath = created.get("sharePath").asText();

        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "GOING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRsvp").value("GOING"))
                .andExpect(jsonPath("$.goingCount").value(1));

        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "GOING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(1));

        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INTERESTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRsvp").value("INTERESTED"))
                .andExpect(jsonPath("$.goingCount").value(0))
                .andExpect(jsonPath("$.interestedCount").value(1));

        mockMvc.perform(get("/api/v1/study-servers/{id}/events", studyServerId)
                        .param("filter", "UPCOMING")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].id").value(eventId.toString()));

        mockMvc.perform(get("/api/v1/study-servers/{id}/events", studyServerId)
                        .param("filter", "GOING")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(0));

        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "GOING"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/study-servers/{id}/events", studyServerId)
                        .param("filter", "GOING")
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1));

        mockMvc.perform(get("/api/v1/study-servers/{id}/events/{eventId}/ics", studyServerId, eventId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VEVENT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Guest talk: AI in industry")));

        mockMvc.perform(get("/api/v1/study-servers/{id}/events/{eventId}", studyServerId, eventId)
                        .with(asUser(outsiderUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/study-servers/{id}/events", studyServerId)
                        .with(asUser(outsiderUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Unauthorized",
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString(),
                                "visibility", "HUB"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/study-servers/{id}/events/{eventId}", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Guest talk: AI careers",
                                "description", "Updated description",
                                "location", "Hall B",
                                "startsAt", startsAt.toString(),
                                "endsAt", endsAt.toString(),
                                "visibility", "HUB"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Guest talk: AI careers"));

        mockMvc.perform(post("/api/v1/study-servers/{id}/events/{eventId}/cancel", studyServerId, eventId)
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=?", Integer.class, "EVENT:" + eventId)).isEqualTo(3);

        mockMvc.perform(put("/api/v1/study-servers/{id}/events/{eventId}/rsvp", studyServerId, eventId)
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "GOING"))))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(sharePath)
                .isEqualTo("/app/servers/" + studyServerId + "/community/events?event=" + eventId);
    }

    private UUID createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Events Hub"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
