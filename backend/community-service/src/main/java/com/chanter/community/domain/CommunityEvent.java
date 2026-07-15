package com.chanter.community.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record CommunityEvent(
        UUID id,
        UUID studyServerId,
        String title,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        Integer capacity,
        CommunityEventVisibility visibility,
        UUID courseId,
        UUID cohortId,
        UUID createdByUserId,
        CommunityEventStatus status,
        Instant createdAt,
        Instant updatedAt,
        long goingCount,
        long interestedCount,
        CommunityEventRsvpStatus viewerRsvp
) {
    public Optional<UUID> courseIdOptional() {
        return Optional.ofNullable(courseId);
    }

    public Optional<UUID> cohortIdOptional() {
        return Optional.ofNullable(cohortId);
    }

    public Optional<CommunityEventRsvpStatus> viewerRsvpOptional() {
        return Optional.ofNullable(viewerRsvp);
    }

    public boolean cancelled() {
        return status == CommunityEventStatus.CANCELLED;
    }
}
