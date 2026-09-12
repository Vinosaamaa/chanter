package com.chanter.auth.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void lockUser(UUID userId);

    void createSession(UUID sessionId, UUID userId, UUID tokenId, String tokenHash,
                       Instant now, Instant expiresAt, String userAgent);

    /** Commits rotation or family revocation before returning, including when rotation is rejected. */
    Optional<SessionRecord> rotate(String tokenHash, UUID replacementId, String replacementHash, Instant now);

    List<SessionRecord> findActiveSessions(UUID userId, Instant now);

    Optional<UUID> findSessionIdByTokenHash(String tokenHash);

    boolean revokeSession(UUID userId, UUID sessionId, Instant revokedAt);

    void revokeByTokenHash(String tokenHash, Instant revokedAt);

    void revokeAllForUser(UUID userId, Instant revokedAt);

    record SessionRecord(UUID id, UUID userId, Instant createdAt, Instant lastUsedAt,
                         Instant expiresAt, String userAgent) {}
}
