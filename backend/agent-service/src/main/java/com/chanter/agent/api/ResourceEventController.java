package com.chanter.agent.api;

import com.chanter.agent.application.ResourceIngestionJobs;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal")
public class ResourceEventController {
    private final DurableConsumer consumer;
    private final InternalEventAccess access;
    private final ResourceIngestionJobs jobs;
    private final ObjectMapper mapper;
    public ResourceEventController(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            ResourceIngestionJobs jobs, ObjectMapper mapper, @Value("${chanter.internal-service-token}") String token) {
        this.consumer = new DurableConsumer(jdbc, new TransactionTemplate(transactions));
        this.access = new InternalEventAccess(token); this.jobs = jobs; this.mapper = mapper;
    }
    @PostMapping("/events") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestBody DurableEvent event,
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        try {
            event.validate();
            var change = mapper.readValue(event.payload(), ResourceChanged.class);
            if (change == null) throw new IllegalArgumentException();
            change.validate();
            if (!event.producer().equals("media") || !event.kind().equals("RESOURCE_CHANGED")
                    || !event.aggregateKey().equals("RESOURCE:" + change.resourceId())) throw new IllegalArgumentException();
            consumer.apply(event, change.deleted(), () -> jobs.accept(event, change));
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource event");
        }
    }
    @GetMapping("/resource-ingestion/{resourceId}/status")
    public ResourceIngestionJobs.State status(@PathVariable UUID resourceId, @RequestParam UUID eventId,
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        return jobs.status(resourceId, eventId);
    }
}
