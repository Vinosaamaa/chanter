package com.chanter.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

public final class NotificationEventWriter {
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public NotificationEventWriter(DurableOutbox outbox, ObjectMapper mapper,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.outbox = outbox; this.mapper = mapper;this.jdbc=jdbc;
    }
    public void append(Map<String, Object> notification) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Notification payload requires source transaction");
        if(!(notification.get("userId") instanceof UUID user)) throw new IllegalArgumentException("Notification recipient required");
        jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='ACCOUNT' AND target_id=?",Integer.class,user)>0) return;
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
