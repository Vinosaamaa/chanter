package com.chanter.auth.moderation;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.auth.application.AuthSessionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Platform grants are checked from their owning database, never from product roles or headers. */
@Service
public class OperatorAccess {
    private final JdbcTemplate jdbc;
    private final JwtTokenService tokens;
    private final ModerationRestrictions restrictions;
    private final AuthSessionService sessions;

    public OperatorAccess(JdbcTemplate jdbc, JwtTokenService tokens, ModerationRestrictions restrictions, AuthSessionService sessions) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.restrictions = restrictions;
        this.sessions = sessions;
    }

    public Operator requireRole(String authorization) {
        // Shared with terminal account cleanup: operator authority precedes any account row lock.
        jdbc.queryForObject("SELECT id FROM platform_operator_lock WHERE id=1 FOR UPDATE",Integer.class);
        UUID user = tokens.parseUserId(authorization);
        Operator operator = jdbc.query("SELECT role FROM platform_operators WHERE user_id=? AND revoked_at IS NULL FOR UPDATE",
                (rs, row) -> new Operator(user, Role.valueOf(rs.getString(1))), user).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform operator access required"));
        restrictions.requireActiveAccount(user);
        sessions.requireActiveAccessSession(authorization);
        return operator;
    }

    public Operator requireStepUp(String authorization, String stepUp) {
        Operator operator = requireRole(authorization);
        if (stepUp == null || stepUp.length() > 128) throw stepUpRequired();
        boolean current = jdbc.query("""
                SELECT expires_at FROM platform_step_up
                WHERE token_hash=? AND user_id=? AND access_token_hash=?
                """, (rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
                hash(stepUp), operator.userId(), hash(authorization)).stream().anyMatch(time -> time.isAfter(Instant.now()));
        if (!current) throw stepUpRequired();
        return operator;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ResponseStatusException stepUpRequired() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Recent operator verification required");
    }

    public enum Role { REVIEWER, ADMIN }
    public record Operator(UUID userId, Role role) {
        public void requireAdmin() {
            if (role != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator required");
        }
    }
}
