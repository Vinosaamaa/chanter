package com.chanter.auth.moderation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public Restriction get(UUID report, UUID id) {
        return jdbc.query("SELECT * FROM moderation_restrictions WHERE report_id=? AND id=? FOR UPDATE",
                (rs, row) -> restriction(rs), report, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restriction not found"));
    }

    public List<Restriction> list(UUID report) {
        return jdbc.query("SELECT * FROM moderation_restrictions WHERE report_id=? ORDER BY starts_at DESC,id LIMIT 100",
                (rs, row) -> restriction(rs), report);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean revoke(UUID report, UUID id, UUID actor, String reason, UUID correlation) {
        OperatorRoles.requireReason(reason);
        Restriction record = get(report, id);
        if (record.revokedAt() != null) return false;
        Instant now = Instant.now();
        boolean previouslyRestricted = isRestricted(record.type(), record.targetId(), now);
        jdbc.update("UPDATE moderation_restrictions SET revoked_at=? WHERE id=?", now.atOffset(ZoneOffset.UTC), id);
        boolean stillRestricted = isRestricted(record.type(), record.targetId(), now);
        audit.append(actor, "RESTRICTION_REVOKED", record.type()+":"+record.targetId(), reason, correlation,
                previouslyRestricted ? "RESTRICTED" : "UNRESTRICTED", stillRestricted ? "RESTRICTED" : "UNRESTRICTED");
        return true;
    }

    private static Restriction restriction(java.sql.ResultSet row) throws java.sql.SQLException {
        OffsetDateTime revoked = row.getObject("revoked_at", OffsetDateTime.class);
        return new Restriction(row.getObject("id", UUID.class), row.getString("target_type"),
                row.getObject("target_id", UUID.class), row.getString("reason"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(), revoked == null ? null : revoked.toInstant());
    }

    public record Restriction(UUID id, String type, UUID targetId, String reason, Instant startsAt,
            Instant expiresAt, Instant revokedAt) { }
}
