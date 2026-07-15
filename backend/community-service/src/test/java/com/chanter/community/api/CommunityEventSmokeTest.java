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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
