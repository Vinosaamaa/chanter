package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.chanter.agent.application.ResourceIngestionJobs;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceEventHttpTest {
    private static final String TOKEN = "test-internal-service-token-for-agent";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ResourceIngestionJobs jobs;

    @Test void jsonNullIsRejectedAsBadRequestBeforeConsumption() throws Exception {
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("null")).andExpect(status().isBadRequest());
    }

    @Test void internalEventAuthenticationScopeAndReplayAreEnforced() throws Exception {
        var resource = UUID.randomUUID();
        var change = new ResourceChanged(resource, UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), "notes.txt", true, false);
        var event = new DurableEvent(UUID.randomUUID(), 1, "media", 1, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(change));
        mvc.perform(post("/api/v1/internal/events").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isUnauthorized());
        for (int delivery = 0; delivery < 2; delivery++) {
            mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        }
        assertThat(jobs.status(resource, event.id()).status()).isEqualTo("PENDING");
        var invalid = new DurableEvent(UUID.randomUUID(), 1, "community", 2, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(change));
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(invalid))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/internal/resource-ingestion/{id}/status", resource).param("eventId", event.id().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/internal/resource-ingestion/{id}/status", resource).param("eventId", event.id().toString())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)).andExpect(status().isOk());
        // Leave no queued work for other integration tests sharing this application context.
        var deleted = new ResourceChanged(resource, null, null, null, null, false, true);
        var terminal = new DurableEvent(UUID.randomUUID(), 1, "media", 3, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(deleted));
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(terminal))).andExpect(status().isNoContent());
    }
}
