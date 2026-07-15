package com.chanter.community.api;

import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.CommunityAnnouncement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CommunityAnnouncementResponse(
        UUID id,
        UUID studyServerId,
        UUID authorUserId,
        String authorDisplayName,
        String title,
        String body,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        boolean viewerLiked,
        boolean canEdit
) {
    static CommunityAnnouncementResponse from(
            CommunityAnnouncement announcement,
            AuthUserProfile author,
            boolean canEdit
    ) {
        String displayName = author == null || author.displayName() == null || author.displayName().isBlank()
                ? "Member"
                : author.displayName();
        return new CommunityAnnouncementResponse(
                announcement.id(),
                announcement.studyServerId(),
                announcement.authorUserId(),
                displayName,
                announcement.title(),
                announcement.body(),
                announcement.status().name(),
                announcement.createdAt(),
                announcement.updatedAt(),
                announcement.likeCount(),
                announcement.viewerLiked(),
                canEdit
        );
    }

    public record CommunityAnnouncementListResponse(List<CommunityAnnouncementResponse> announcements) {
        static CommunityAnnouncementListResponse from(
                List<CommunityAnnouncement> announcements,
                Map<UUID, AuthUserProfile> profiles,
                boolean canEdit
        ) {
            return new CommunityAnnouncementListResponse(
                    announcements.stream()
                            .map(item -> CommunityAnnouncementResponse.from(
                                    item,
                                    profiles.get(item.authorUserId()),
                                    canEdit
                            ))
                            .toList()
            );
        }
    }
}
