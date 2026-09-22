package com.chanter.auth.infra;

import com.chanter.auth.application.RefreshTokenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {
    private static final RowMapper<SessionRecord> SESSION_MAPPER = (rs, row) -> new SessionRecord(
            rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_used_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(), rs.getString("user_agent"));
    private final JdbcTemplate jdbc;

    public JdbcRefreshTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockUser(UUID userId) {
        // Common lock order with password reset: user first, then session, then token.
        jdbc.query("SELECT id FROM auth_users WHERE id = ? FOR UPDATE", (rs, row) -> rs.getObject(1), userId);
    }

    @Override
    @Transactional
    public void createSession(UUID sessionId, UUID userId, UUID tokenId, String tokenHash,
                              Instant now, Instant expiresAt, String userAgent) {
        lockUser(userId);
        jdbc.update("""
                INSERT INTO auth_sessions (id, user_id, created_at, last_used_at, expires_at, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sessionId, userId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(expiresAt), userAgent);
        saveToken(tokenId, userId, sessionId, tokenHash, expiresAt);
    }

    @Override
    @Transactional
    public Optional<SessionRecord> rotate(String tokenHash, UUID replacementId, String replacementHash, Instant now) {
        var identity = tokenIdentity(tokenHash);
        if (identity.isEmpty()) return Optional.empty();
        UUID userId = identity.get().userId();
        UUID sessionId = identity.get().sessionId();
        lockUser(userId);
        var sessions = jdbc.query("SELECT * FROM auth_sessions WHERE id = ? FOR UPDATE", SESSION_MAPPER, sessionId);
        if (sessions.isEmpty()) return Optional.empty();
        SessionRecord session = sessions.getFirst();
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT revoked_at IS NULL AND expires_at > ? FROM auth_sessions WHERE id = ?",
                Boolean.class, Timestamp.from(now), sessionId));
        if (!active) return Optional.empty();
        boolean unused = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT consumed_at IS NULL AND revoked_at IS NULL AND expires_at > ?
                FROM auth_refresh_tokens WHERE token_hash = ?
                """, Boolean.class, Timestamp.from(now), tokenHash));
        if (!unused) {
            // Return failure instead of throwing: the revocation must survive the HTTP 401.
            revokeFamily(sessionId, now);
            return Optional.empty();
        }
        jdbc.update("UPDATE auth_refresh_tokens SET consumed_at = ? WHERE token_hash = ?", Timestamp.from(now), tokenHash);
        saveToken(replacementId, userId, sessionId, replacementHash, session.expiresAt());
        jdbc.update("UPDATE auth_sessions SET last_used_at = ? WHERE id = ?", Timestamp.from(now), sessionId);
        return Optional.of(session);
    }

    @Override
    public List<SessionRecord> findActiveSessions(UUID userId, Instant now) {
        return jdbc.query("""
                SELECT * FROM auth_sessions WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
                ORDER BY last_used_at DESC, id
                """, SESSION_MAPPER, userId, Timestamp.from(now));
    }

    @Override
    public Optional<UUID> findSessionIdByTokenHash(String tokenHash) {
        return tokenIdentity(tokenHash).map(TokenIdentity::sessionId);
    }

    @Override
    @Transactional
    public boolean revokeSession(UUID userId, UUID sessionId, Instant revokedAt) {
        lockUser(userId);
        var owned = jdbc.query("SELECT id FROM auth_sessions WHERE id = ? AND user_id = ? FOR UPDATE",
                (rs, row) -> rs.getObject(1), sessionId, userId);
        if (owned.isEmpty()) return false;
        revokeFamily(sessionId, revokedAt);
        return true;
    }

    @Override
    @Transactional
    public void revokeByTokenHash(String tokenHash, Instant revokedAt) {
        tokenIdentity(tokenHash).ifPresent(token -> revokeSession(token.userId(), token.sessionId(), revokedAt));
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        lockUser(userId);
        jdbc.update("UPDATE auth_sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt), userId);
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt), userId);
    }

    private void revokeFamily(UUID sessionId, Instant revokedAt) {
        jdbc.update("UPDATE auth_sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL", Timestamp.from(revokedAt), sessionId);
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at = ? WHERE session_id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt), sessionId);
    }

    private Optional<TokenIdentity> tokenIdentity(String tokenHash) {
        return jdbc.query("SELECT user_id, session_id FROM auth_refresh_tokens WHERE token_hash = ?",
                (rs, row) -> new TokenIdentity(rs.getObject("user_id", UUID.class), rs.getObject("session_id", UUID.class)),
                tokenHash).stream().findFirst();
    }

    private void saveToken(UUID id, UUID userId, UUID sessionId, String tokenHash, Instant expiresAt) {
        jdbc.update("INSERT INTO auth_refresh_tokens (id, user_id, session_id, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, sessionId, tokenHash, Timestamp.from(expiresAt));
    }

    private record TokenIdentity(UUID userId, UUID sessionId) {}
}
