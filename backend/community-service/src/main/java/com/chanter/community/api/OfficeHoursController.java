package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.OfficeHoursService;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import com.chanter.community.domain.VoicePresence;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class OfficeHoursController {

    private final OfficeHoursService officeHoursService;

    public OfficeHoursController(OfficeHoursService officeHoursService) {
        this.officeHoursService = officeHoursService;
    }

    @PostMapping("/cohorts/{cohortId}/office-hours")
    public ResponseEntity<OfficeHoursSessionResponse> scheduleOfficeHours(
            @PathVariable UUID cohortId,
            @Valid @RequestBody ScheduleOfficeHoursRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID instructorUserId
    ) {
        OfficeHoursSession session = officeHoursService.scheduleOfficeHours(
                cohortId,
                instructorUserId,
                request.startsAt(),
                request.endsAt()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ServiceInfo.API_V1_PREFIX + "/office-hours/{sessionId}")
                .buildAndExpand(session.id())
                .toUri();

        return ResponseEntity.created(location).body(OfficeHoursSessionResponse.from(session));
    }

    @GetMapping("/cohorts/{cohortId}/office-hours")
    public OfficeHoursSessionListResponse listOfficeHoursSessions(
            @PathVariable UUID cohortId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return OfficeHoursSessionListResponse.from(
                officeHoursService.listOfficeHoursSessions(cohortId, viewerUserId)
        );
    }

    @GetMapping("/office-hours/{sessionId}")
    public OfficeHoursSessionResponse getOfficeHoursSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return OfficeHoursSessionResponse.from(
                officeHoursService.findOfficeHoursSession(sessionId, viewerUserId)
        );
    }

    @PatchMapping("/office-hours/{sessionId}")
    public OfficeHoursSessionResponse updateOfficeHoursSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateOfficeHoursRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return OfficeHoursSessionResponse.from(officeHoursService.updateSession(
                sessionId,
                actorUserId,
                request.startsAt(),
                request.endsAt()
        ));
    }

    @PostMapping("/office-hours/{sessionId}/participants")
    public ResponseEntity<OfficeHoursParticipantResponse> joinSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return ResponseEntity.created(
                        ServletUriComponentsBuilder.fromCurrentRequest().build().toUri()
                )
                .body(OfficeHoursParticipantResponse.from(officeHoursService.joinSession(sessionId, userId)));
    }

    @GetMapping("/office-hours/{sessionId}/participants")
    public OfficeHoursParticipantListResponse listParticipants(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return OfficeHoursParticipantListResponse.from(
                officeHoursService.listParticipants(sessionId, viewerUserId)
        );
    }

    @PatchMapping("/office-hours/{sessionId}/participants/me/hand")
    public OfficeHoursParticipantResponse updateHandRaised(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateOfficeHoursHandRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return OfficeHoursParticipantResponse.from(
                officeHoursService.updateHandRaised(sessionId, userId, request.raised())
        );
    }

    @PatchMapping("/office-hours/{sessionId}/participants/{userId}/speaking")
    public OfficeHoursParticipantResponse updateSpeakingAccess(
            @PathVariable UUID sessionId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateOfficeHoursSpeakingRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return OfficeHoursParticipantResponse.from(officeHoursService.updateSpeakingAccess(
                sessionId,
                userId,
                actorUserId,
                request.canSpeak()
        ));
    }

    @DeleteMapping("/office-hours/{sessionId}/participants/me")
    public ResponseEntity<Void> leaveSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        officeHoursService.leaveSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/office-hours/{sessionId}")
    public OfficeHoursSessionResponse cancelOfficeHoursSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return OfficeHoursSessionResponse.from(officeHoursService.cancelSession(sessionId, actorUserId));
    }

    @PostMapping("/office-hours/{sessionId}/waitlist")
    public ResponseEntity<OfficeHoursWaitlistEntryResponse> joinWaitlist(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID learnerUserId
    ) {
        OfficeHoursWaitlistEntry entry = officeHoursService.joinWaitlist(sessionId, learnerUserId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ServiceInfo.API_V1_PREFIX + "/office-hours/{sessionId}/waitlist")
                .buildAndExpand(sessionId)
                .toUri();

        return ResponseEntity.created(location).body(OfficeHoursWaitlistEntryResponse.from(entry));
    }

    @GetMapping("/office-hours/{sessionId}/waitlist")
    public OfficeHoursWaitlistListResponse listWaitlist(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return OfficeHoursWaitlistListResponse.from(
                officeHoursService.listWaitlist(sessionId, viewerUserId)
        );
    }

    @PostMapping("/office-hours/{sessionId}/admit-next")
    public OfficeHoursWaitlistEntryResponse admitNextLearner(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return OfficeHoursWaitlistEntryResponse.from(
                officeHoursService.admitNextLearner(sessionId, actorUserId)
        );
    }

    @PostMapping("/office-hours/{sessionId}/voice-join")
    public ResponseEntity<VoicePresenceResponse> joinVoice(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        VoicePresence presence = officeHoursService.joinVoiceAsManager(sessionId, actorUserId);
        return ResponseEntity.ok(VoicePresenceResponse.from(presence));
    }

    @PostMapping("/office-hours/{sessionId}/learner-voice-join")
    public ResponseEntity<VoicePresenceResponse> joinVoiceAsLearner(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID learnerUserId
    ) {
        VoicePresence presence = officeHoursService.joinVoiceAsAdmittedLearner(
                sessionId,
                learnerUserId
        );
        return ResponseEntity.ok(VoicePresenceResponse.from(presence));
    }

    @PostMapping("/office-hours/{sessionId}/media-token")
    public VoiceMediaTokenResponse issueMediaToken(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return VoiceMediaTokenResponse.from(officeHoursService.issueOfficeHoursMediaToken(sessionId, userId));
    }

    @PostMapping("/office-hours/{sessionId}/start")
    public OfficeHoursSessionResponse startSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return OfficeHoursSessionResponse.from(officeHoursService.startSession(sessionId, actorUserId));
    }

    @PostMapping("/office-hours/{sessionId}/end")
    public OfficeHoursSessionResponse endSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return OfficeHoursSessionResponse.from(
                officeHoursService.endSession(sessionId, actorUserId)
        );
    }
}
