package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CohortRosterMember(
        UUID userId,
        UUID invitationId,
        String displayName,
        String email,
        String role,
        String status,
        UUID assignedTeachingAssistantUserId,
        Instant enrolledAt
) {
}
