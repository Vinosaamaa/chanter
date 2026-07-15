package com.chanter.community.application;

import com.chanter.community.domain.CommunityAnnouncement;
import com.chanter.community.domain.CommunityAnnouncementStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityAnnouncementRepository {

    CommunityAnnouncement save(CommunityAnnouncement announcement);

    CommunityAnnouncement update(CommunityAnnouncement announcement);

    Optional<CommunityAnnouncement> findById(UUID announcementId, UUID viewerUserId);

    List<CommunityAnnouncement> findByStudyServer(
            UUID studyServerId,
            UUID viewerUserId,
            CommunityAnnouncementStatus status
    );

    void setStatus(UUID announcementId, CommunityAnnouncementStatus status, Instant updatedAt, Instant archivedAt);

    void upsertReaction(UUID announcementId, UUID userId, Instant updatedAt);

    void deleteReaction(UUID announcementId, UUID userId);
}
