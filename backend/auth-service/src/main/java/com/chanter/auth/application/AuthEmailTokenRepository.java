package com.chanter.auth.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthEmailTokenRepository {

    void save(UUID id, UUID userId, String tokenHash, String purpose, Instant expiresAt);

    Optional<TokenRecord> findActiveByTokenHash(String tokenHash, String purpose, Instant now);

    void markUsed(UUID id, Instant usedAt);

    void invalidateActiveForUser(UUID userId, String purpose, Instant usedAt);

    record TokenRecord(UUID id, UUID userId, Instant expiresAt) {
    }
}
