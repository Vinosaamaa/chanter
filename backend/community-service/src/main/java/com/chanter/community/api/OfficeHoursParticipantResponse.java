package com.chanter.community.api;

import com.chanter.community.domain.OfficeHoursParticipant;
import java.time.Instant;
import java.util.UUID;

public record OfficeHoursParticipantResponse(
        UUID sessionId,
        UUID userId,
        boolean canSpeak,
        boolean handRaised,
        Instant joinedAt,
        Instant updatedAt
) {
    static OfficeHoursParticipantResponse from(OfficeHoursParticipant participant) {
        return new OfficeHoursParticipantResponse(
                participant.sessionId(),
                participant.userId(),
                participant.canSpeak(),
                participant.handRaised(),
                participant.joinedAt(),
                participant.updatedAt()
        );
    }
}
