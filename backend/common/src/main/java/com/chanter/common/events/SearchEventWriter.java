package com.chanter.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class SearchEventWriter {
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    public SearchEventWriter(DurableOutbox outbox, ObjectMapper mapper) { this.outbox = outbox; this.mapper = mapper; }
    public void append(SearchChange change) {
        try {
            outbox.append("search", change.type(), change.type() + ":" + change.sourceId(), mapper.writeValueAsString(change));
        } catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid search event", exception); }
    }
}
