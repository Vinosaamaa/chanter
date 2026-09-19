package com.chanter.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class NotificationEventWriter {
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    public NotificationEventWriter(DurableOutbox outbox, ObjectMapper mapper) { this.outbox = outbox; this.mapper = mapper; }
    public void append(Map<String, Object> notification) {
        try {
            String key = "NOTIFICATION:" + notification.get("userId") + ":" + notification.get("sourceType")
                    + ":" + notification.get("sourceId") + ":" + notification.get("kind");
            outbox.append("notification", "NOTIFICATION", key, mapper.writeValueAsString(notification));
        } catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid notification event", exception); }
    }
}
