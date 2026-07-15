package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record StudyServerInvitation(
        UUID id,
        UUID studyServerId,
        UUID invitedUserId,
        String email,
        UUID invitedByUserId,
        StudyServerInvitationStatus status,
        Instant createdAt,
        Instant resolvedAt
) {
}
