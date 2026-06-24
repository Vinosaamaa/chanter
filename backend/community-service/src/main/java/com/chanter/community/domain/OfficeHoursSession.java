package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record OfficeHoursSession(
        UUID id,
        UUID cohortId,
        UUID voiceChannelId,
        UUID scheduledByUserId,
        Instant startsAt,
        Instant endsAt,
        OfficeHoursSessionStatus status,
        Instant createdAt
) {
}
