package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.CommunityAnnouncementService;
import com.chanter.community.application.CourseRepository;
import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.CommunityAnnouncement;
import com.chanter.community.domain.CommunityAnnouncementStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}/announcements")
public class CommunityAnnouncementController {

    private final CommunityAnnouncementService announcementService;
    private final CourseRepository courseRepository;

    public CommunityAnnouncementController(
            CommunityAnnouncementService announcementService,
            CourseRepository courseRepository
    ) {
        this.announcementService = announcementService;
        this.courseRepository = courseRepository;
    }

    @GetMapping
    public CommunityAnnouncementResponse.CommunityAnnouncementListResponse listAnnouncements(
            @PathVariable UUID studyServerId,
            @RequestParam(required = false) String status,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        boolean canEdit = courseRepository.isStudyServerOwner(studyServerId, viewerUserId);
        List<CommunityAnnouncement> announcements = announcementService.listAnnouncements(
                studyServerId,
                viewerUserId,
                CommunityAnnouncementStatus.fromApiValue(status)
        );
        Map<UUID, AuthUserProfile> profiles = announcementService.profilesFor(announcements);
        return CommunityAnnouncementResponse.CommunityAnnouncementListResponse.from(
                announcements,
                profiles,
                canEdit
        );
    }

    @GetMapping("/{announcementId}")
    public CommunityAnnouncementResponse getAnnouncement(
            @PathVariable UUID studyServerId,
            @PathVariable UUID announcementId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        CommunityAnnouncement announcement = announcementService.getAnnouncement(
                studyServerId,
                announcementId,
                viewerUserId
        );
        boolean canEdit = courseRepository.isStudyServerOwner(studyServerId, viewerUserId);
        Map<UUID, AuthUserProfile> profiles = announcementService.profilesFor(List.of(announcement));
        return CommunityAnnouncementResponse.from(
                announcement,
                profiles.get(announcement.authorUserId()),
                canEdit
        );
    }

    @PostMapping
    public ResponseEntity<CommunityAnnouncementResponse> createAnnouncement(
            @PathVariable UUID studyServerId,
            @Valid @RequestBody CreateCommunityAnnouncementRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityAnnouncement announcement = announcementService.createAnnouncement(
                studyServerId,
                actorUserId,
                request.title(),
                request.body()
        );
        Map<UUID, AuthUserProfile> profiles = announcementService.profilesFor(List.of(announcement));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{announcementId}")
                .buildAndExpand(announcement.id())
                .toUri();
        return ResponseEntity.created(location).body(CommunityAnnouncementResponse.from(
                announcement,
                profiles.get(announcement.authorUserId()),
                true
        ));
    }

    @PatchMapping("/{announcementId}")
    public CommunityAnnouncementResponse updateAnnouncement(
            @PathVariable UUID studyServerId,
            @PathVariable UUID announcementId,
            @Valid @RequestBody UpdateCommunityAnnouncementRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityAnnouncement announcement = announcementService.updateAnnouncement(
                studyServerId,
                announcementId,
                actorUserId,
                request.title(),
                request.body()
        );
        Map<UUID, AuthUserProfile> profiles = announcementService.profilesFor(List.of(announcement));
        return CommunityAnnouncementResponse.from(
                announcement,
                profiles.get(announcement.authorUserId()),
                true
        );
    }

    @PostMapping("/{announcementId}/archive")
    public CommunityAnnouncementResponse archiveAnnouncement(
            @PathVariable UUID studyServerId,
            @PathVariable UUID announcementId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityAnnouncement announcement = announcementService.archiveAnnouncement(
                studyServerId,
                announcementId,
                actorUserId
        );
        Map<UUID, AuthUserProfile> profiles = announcementService.profilesFor(List.of(announcement));
        return CommunityAnnouncementResponse.from(
                announcement,
                profiles.get(announcement.authorUserId()),
                true
        );
    }

    @PutMapping("/{announcementId}/reactions")
    public CommunityAnnouncementResponse upsertReaction(
            @PathVariable UUID studyServerId,
            @PathVariable UUID announcementId,
            @Valid @RequestBody UpsertAnnouncementReactionRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityAnnouncement announcement = announcementService.upsertLike(
                studyServerId,
                announcementId,
                actorUserId,
                Boolean.TRUE.equals(request.liked())
        );
        boolean canEdit = courseRepository.isStudyServerOwner(studyServerId, actorUserId);
        Map<UUID, AuthUserProfile> profiles = announcementService.profilesFor(List.of(announcement));
        return CommunityAnnouncementResponse.from(
                announcement,
                profiles.get(announcement.authorUserId()),
                canEdit
        );
    }
}
