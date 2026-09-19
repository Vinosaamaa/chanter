package com.chanter.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public final class ResourceEventWriter {
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    public ResourceEventWriter(DurableOutbox outbox, ObjectMapper mapper) { this.outbox = outbox; this.mapper = mapper; }
    public UUID append(ResourceChanged change) {
        change.validate();
        try {
            return outbox.append("agent", "RESOURCE_CHANGED", "RESOURCE:" + change.resourceId(), mapper.writeValueAsString(change));
        } catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid resource change", exception); }
    }
}
