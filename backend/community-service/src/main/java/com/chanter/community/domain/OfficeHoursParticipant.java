package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record OfficeHoursParticipant(
        UUID sessionId,
        UUID userId,
        boolean canSpeak,
        boolean handRaised,
        boolean active,
        Instant joinedAt,
        Instant updatedAt
) {
}
