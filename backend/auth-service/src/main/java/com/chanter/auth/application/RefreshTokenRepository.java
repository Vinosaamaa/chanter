package com.chanter.auth.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void save(UUID id, UUID userId, String tokenHash, Instant expiresAt);

    Optional<UUID> findActiveUserIdByTokenHash(String tokenHash, Instant now);

    Optional<UUID> consumeActiveUserIdByTokenHash(String tokenHash, Instant now);

    void revokeByTokenHash(String tokenHash, Instant revokedAt);

    void revokeAllForUser(UUID userId, Instant revokedAt);
}
