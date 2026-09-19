package com.chanter.auth.lifecycle;

import com.chanter.auth.application.AuthSessionService;
import com.chanter.auth.application.RefreshTokenRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Session creation is recent login proof. Rotation and passive use never advance it. */
@Component
public class LifecycleSessionAccess {
    public static final Duration RECENT_LOGIN = Duration.ofMinutes(5);
    private final AuthSessionService sessions;
    private final RefreshTokenRepository tokens;
    private final JdbcTemplate jdbc;
    public LifecycleSessionAccess(AuthSessionService sessions, RefreshTokenRepository tokens, JdbcTemplate jdbc) {
        this.sessions = sessions; this.tokens = tokens; this.jdbc = jdbc;
    }
    public UUID require(String authorization, boolean recent) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Lifecycle access requires its owning transaction");
        var token = sessions.requireActiveAccessSession(authorization);
        tokens.lockUser(token.userId());
        var rows = jdbc.query("""
            SELECT created_at FROM auth_sessions WHERE id=? AND user_id=? AND revoked_at IS NULL AND expires_at>? FOR UPDATE
            """, (rs, row) -> rs.getTimestamp(1).toInstant(), token.sessionId(), token.userId(), Timestamp.from(Instant.now()));
        Instant now = Instant.now();
        if (rows.isEmpty() || !token.expiresAt().isAfter(now)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SESSION_INACTIVE");
        // Recheck the owning access policy after waiting for locks. #249 adds current account restrictions here.
        sessions.requireActiveAccessSession(authorization);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_journal WHERE target_kind='ACCOUNT' AND target_id=?", Integer.class, token.userId()) > 0)
            throw new ResponseStatusException(HttpStatus.GONE, "ACCOUNT_DELETED");
        if (recent && (rows.getFirst().isBefore(now.minus(RECENT_LOGIN)) || rows.getFirst().isAfter(now.plusSeconds(5))))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "RECENT_LOGIN_REQUIRED");
        return token.userId();
    }
}
