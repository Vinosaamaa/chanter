package com.chanter.community.application;

import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.CommunityAnnouncement;
import com.chanter.community.domain.CommunityAnnouncementStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityAnnouncementService {

    private final CommunityAnnouncementRepository announcementRepository;
    private final StudyServerRepository studyServerRepository;
    private final CourseRepository courseRepository;
    private final AuthUserDirectoryClient authUserDirectoryClient;
    private final Clock clock;

    public CommunityAnnouncementService(
            CommunityAnnouncementRepository announcementRepository,
            StudyServerRepository studyServerRepository,
            CourseRepository courseRepository,
            AuthUserDirectoryClient authUserDirectoryClient,
            Clock clock
    ) {
        this.announcementRepository = announcementRepository;
        this.studyServerRepository = studyServerRepository;
        this.courseRepository = courseRepository;
        this.authUserDirectoryClient = authUserDirectoryClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommunityAnnouncement> listAnnouncements(
            UUID studyServerId,
            UUID viewerUserId,
            CommunityAnnouncementStatus status
    ) {
        requireStudyServerMember(studyServerId, viewerUserId);
        requireStudyServerExists(studyServerId);
        CommunityAnnouncementStatus effectiveStatus = status == null
                ? CommunityAnnouncementStatus.PUBLISHED
                : status;
        if (effectiveStatus == CommunityAnnouncementStatus.ARCHIVED
                && !courseRepository.isStudyServerOwner(studyServerId, viewerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners can view archived announcements");
        }
        return announcementRepository.findByStudyServer(studyServerId, viewerUserId, effectiveStatus);
    }

    @Transactional(readOnly = true)
    public CommunityAnnouncement getAnnouncement(UUID studyServerId, UUID announcementId, UUID viewerUserId) {
        requireStudyServerMember(studyServerId, viewerUserId);
        return requireAnnouncementOnServer(studyServerId, announcementId, viewerUserId);
    }

    @Transactional
    public CommunityAnnouncement createAnnouncement(
            UUID studyServerId,
            UUID actorUserId,
            String title,
            String body
    ) {
        requireStudyServerOwner(studyServerId, actorUserId);
        Instant now = clock.instant();
        CommunityAnnouncement announcement = new CommunityAnnouncement(
                UUID.randomUUID(),
                studyServerId,
                actorUserId,
                title.trim(),
                body.trim(),
                CommunityAnnouncementStatus.PUBLISHED,
                now,
                now,
                null,
                0,
                false
        );
        return announcementRepository.save(announcement);
    }

    @Transactional
    public CommunityAnnouncement updateAnnouncement(
            UUID studyServerId,
            UUID announcementId,
            UUID actorUserId,
            String title,
            String body
    ) {
        requireStudyServerOwner(studyServerId, actorUserId);
        CommunityAnnouncement existing = requireAnnouncementOnServer(studyServerId, announcementId, actorUserId);
        if (existing.archived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived announcements cannot be edited");
        }
        CommunityAnnouncement updated = new CommunityAnnouncement(
                existing.id(),
                existing.studyServerId(),
                existing.authorUserId(),
                title.trim(),
                body.trim(),
                existing.status(),
                existing.createdAt(),
                clock.instant(),
                existing.archivedAt(),
                existing.likeCount(),
                existing.viewerLiked()
        );
        return announcementRepository.update(updated);
    }

    @Transactional
    public CommunityAnnouncement archiveAnnouncement(
            UUID studyServerId,
            UUID announcementId,
            UUID actorUserId
    ) {
        requireStudyServerOwner(studyServerId, actorUserId);
        CommunityAnnouncement existing = requireAnnouncementOnServer(studyServerId, announcementId, actorUserId);
        if (existing.archived()) {
            return existing;
        }
        Instant now = clock.instant();
        announcementRepository.setStatus(announcementId, CommunityAnnouncementStatus.ARCHIVED, now, now);
        return requireAnnouncementOnServer(studyServerId, announcementId, actorUserId);
    }

    @Transactional
    public CommunityAnnouncement upsertLike(
            UUID studyServerId,
            UUID announcementId,
            UUID actorUserId,
            boolean liked
    ) {
        requireStudyServerMember(studyServerId, actorUserId);
        CommunityAnnouncement existing = requireAnnouncementOnServer(studyServerId, announcementId, actorUserId);
        if (existing.archived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived announcements cannot be reacted to");
        }
        if (liked) {
            announcementRepository.upsertReaction(announcementId, actorUserId, clock.instant());
        } else {
            announcementRepository.deleteReaction(announcementId, actorUserId);
        }
        return requireAnnouncementOnServer(studyServerId, announcementId, actorUserId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, AuthUserProfile> profilesFor(List<CommunityAnnouncement> announcements) {
        List<UUID> authorIds = announcements.stream()
                .map(CommunityAnnouncement::authorUserId)
                .distinct()
                .toList();
        Map<UUID, AuthUserProfile> profiles = new LinkedHashMap<>();
        if (authorIds.isEmpty()) {
            return profiles;
        }
        authUserDirectoryClient.findByIds(authorIds)
                .forEach(profile -> profiles.put(profile.userId(), profile));
        return profiles;
    }

    private CommunityAnnouncement requireAnnouncementOnServer(
            UUID studyServerId,
            UUID announcementId,
            UUID viewerUserId
    ) {
        CommunityAnnouncement announcement = announcementRepository.findById(announcementId, viewerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        if (!announcement.studyServerId().equals(studyServerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
        return announcement;
    }

    private void requireStudyServerExists(UUID studyServerId) {
        studyServerRepository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
    }

    private void requireStudyServerMember(UUID studyServerId, UUID userId) {
        if (!studyServerRepository.isStudyServerMember(studyServerId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Study Server membership required");
        }
    }

    private void requireStudyServerOwner(UUID studyServerId, UUID userId) {
        requireStudyServerExists(studyServerId);
        if (!courseRepository.isStudyServerOwner(studyServerId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Study Server owner can manage announcements");
        }
    }
}
