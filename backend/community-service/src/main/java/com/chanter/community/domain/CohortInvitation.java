package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CohortInvitation(
        UUID id,
        UUID cohortId,
        UUID invitedUserId,
        String email,
        UUID invitedByUserId,
        CohortInvitationStatus status,
        Instant createdAt
) {
}
