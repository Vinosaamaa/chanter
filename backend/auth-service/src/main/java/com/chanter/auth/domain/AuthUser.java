package com.chanter.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthUser(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        Instant createdAt
) {
}
