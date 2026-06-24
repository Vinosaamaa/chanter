package com.chanter.community.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudyServer(
        UUID id,
        String name,
        OwnerRole ownerRole,
        SaasPlanTier planTier,
        List<StudyServerChannel> channels,
        Instant createdAt
) {
}
