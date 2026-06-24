package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record OfficeHoursWaitlistEntry(
        UUID sessionId,
        UUID learnerUserId,
        Instant joinedAt,
        OfficeHoursWaitlistStatus status
) {
}
