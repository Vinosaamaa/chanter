package com.chanter.community.api;

import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import java.time.Instant;
import java.util.UUID;

public record OfficeHoursWaitlistEntryResponse(
        UUID sessionId,
        UUID learnerUserId,
        Instant joinedAt,
        String status
) {
    static OfficeHoursWaitlistEntryResponse from(OfficeHoursWaitlistEntry entry) {
        return new OfficeHoursWaitlistEntryResponse(
                entry.sessionId(),
                entry.learnerUserId(),
                entry.joinedAt(),
                entry.status().name()
        );
    }
}
