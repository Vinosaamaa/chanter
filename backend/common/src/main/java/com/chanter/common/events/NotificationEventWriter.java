package com.chanter.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

public final class NotificationEventWriter {
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    public NotificationEventWriter(DurableOutbox outbox, ObjectMapper mapper) { this.outbox = outbox; this.mapper = mapper; }
    public void append(Map<String, Object> notification) {
        notification=neutralQuestionUpdate(notification);
        try {
            String key = aggregateKey((UUID) notification.get("userId"), (String) notification.get("sourceType"),
                    (UUID) notification.get("sourceId"), (String) notification.get("kind"));
            outbox.append("notification", "NOTIFICATION", key, mapper.writeValueAsString(notification));
        } catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid notification event", exception); }
    }
    /** Legacy AI/human replies share one identity. Keep a useful update without retaining reply content. */
    public static Map<String,Object> neutralQuestionUpdate(Map<String,Object> notification) {
        if(!"SUPPORT_QUESTION".equalsIgnoreCase(String.valueOf(notification.get("sourceType")))
                || !"SUPPORT_QUESTION_ANSWERED".equals(notification.get("kind"))) return notification;
        var result=new java.util.LinkedHashMap<>(notification);
        result.put("title","Question update");result.put("bodyPreview",null);result.put("courseLabel",null);
        return result;
    }
    public static String aggregateKey(UUID userId, String sourceType, UUID sourceId, String kind) {
        return "NOTIFICATION:" + userId + ":" + sourceType + ":" + sourceId + ":" + kind;
    }
}
