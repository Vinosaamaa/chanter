package com.chanter.auth.moderation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModerationAudit {
    private final JdbcTemplate jdbc;
    public ModerationAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(UUID actor, String action, String target, String reason, UUID correlation,
            String before, String after) {
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new IllegalArgumentException("An audit reason of at most 2000 characters is required");
        jdbc.update("""
                INSERT INTO moderation_audit(actor_id,action,target,reason,correlation_id,occurred_at,before_state,after_state)
                VALUES(?,?,?,?,?,?,?,?)
                """, actor, action, target, reason.strip(), correlation, OffsetDateTime.now(ZoneOffset.UTC), before, after);
    }
}
