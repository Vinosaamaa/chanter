package com.chanter.community.api;

import com.chanter.community.domain.OfficeHoursSession;
import java.time.Instant;
import java.util.UUID;

public record OfficeHoursSessionResponse(
        UUID id,
        UUID cohortId,
        UUID voiceChannelId,
        UUID scheduledByUserId,
        Instant startsAt,
        Instant endsAt,
        String status,
        Instant createdAt
) {
    static OfficeHoursSessionResponse from(OfficeHoursSession session) {
        return new OfficeHoursSessionResponse(
                session.id(),
                session.cohortId(),
                session.voiceChannelId(),
                session.scheduledByUserId(),
                session.startsAt(),
                session.endsAt(),
                session.status().name(),
                session.createdAt()
        );
    }
}
