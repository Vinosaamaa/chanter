package com.chanter.auth.moderation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Current restrictions are authoritative; delivery delays must never reopen restricted content. */
@Service
public class ModerationRestrictions {
    public static final Set<String> TARGET_TYPES = Set.of("USER", "DM", "MESSAGE", "RESOURCE", "STUDY_SERVER");
    private final JdbcTemplate jdbc;
    private final ModerationAudit audit;
    public ModerationRestrictions(JdbcTemplate jdbc, ModerationAudit audit) { this.jdbc = jdbc; this.audit = audit; }

    public boolean isRestricted(String type, UUID target, Instant at) {
        requireType(type);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM moderation_restrictions WHERE target_type=? AND target_id=?
                AND starts_at<=? AND expires_at>? AND revoked_at IS NULL)
                """, Boolean.class, type, target, at.atOffset(ZoneOffset.UTC), at.atOffset(ZoneOffset.UTC)));
    }

    public void requireActiveAccount(UUID user) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_users WHERE id=?)", Boolean.class, user)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found");
        if (isRestricted("USER", user, Instant.now()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account access is suspended; review your appeal options");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(UUID operation, UUID report, String type, UUID target, UUID actor, String reason, Instant expires, UUID correlation) {
        requireType(type);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (expires == null || !expires.isAfter(now.plusSeconds(59)) || expires.isAfter(now.plus(Duration.ofDays(30))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a restriction duration between one minute and 30 days");
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason of at most 2000 characters is required");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM moderation_restrictions WHERE id=?)", Boolean.class, operation)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This restriction operation was already recorded");
        boolean previouslyRestricted = isRestricted(type, target, now);
        jdbc.update("""
                INSERT INTO moderation_restrictions(id,report_id,target_type,target_id,actor_id,reason,starts_at,expires_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, operation, report, type, target, actor, reason.strip(), now.atOffset(ZoneOffset.UTC), expires.atOffset(ZoneOffset.UTC));
        audit.append(actor, "RESTRICTION_CREATED", type+":"+target, reason, correlation,
                previouslyRestricted ? "RESTRICTED" : "UNRESTRICTED", "RESTRICTED_UNTIL:"+expires);
        if (type.equals("USER")) {
            // Existing access tokens are handled by current-status checks; refresh credentials are also revoked.
            jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", now.atOffset(ZoneOffset.UTC), target);
            jdbc.update("UPDATE auth_refresh_tokens SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", now.atOffset(ZoneOffset.UTC), target);
            jdbc.update("DELETE FROM platform_step_up WHERE user_id=?", target);
        }
    }

    static void requireType(String type) {
        if (!TARGET_TYPES.contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported moderation target");
    }
}
