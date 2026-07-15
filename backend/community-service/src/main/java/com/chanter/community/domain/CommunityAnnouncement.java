package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CommunityAnnouncement(
        UUID id,
        UUID studyServerId,
        UUID authorUserId,
        String title,
        String body,
        CommunityAnnouncementStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        long likeCount,
        boolean viewerLiked
) {
    public boolean archived() {
        return status == CommunityAnnouncementStatus.ARCHIVED;
    }
}
