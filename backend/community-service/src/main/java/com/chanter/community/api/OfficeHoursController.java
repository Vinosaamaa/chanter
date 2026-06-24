package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.community.application.OfficeHoursService;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import com.chanter.community.domain.VoicePresence;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @Valid @RequestBody ScheduleOfficeHoursRequest request
    ) {
        OfficeHoursSession session = officeHoursService.scheduleOfficeHours(
                cohortId,
                request.instructorUserId(),
                request.startsAt(),
                request.endsAt()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionId}")
                .buildAndExpand(session.id())
                .toUri();

        return ResponseEntity.created(location).body(OfficeHoursSessionResponse.from(session));
    }

    @GetMapping("/cohorts/{cohortId}/office-hours")
    public OfficeHoursSessionListResponse listOfficeHoursSessions(
            @PathVariable UUID cohortId,
            @RequestParam UUID viewerUserId
    ) {
        return OfficeHoursSessionListResponse.from(
                officeHoursService.listOfficeHoursSessions(cohortId, viewerUserId)
        );
    }

    @GetMapping("/office-hours/{sessionId}")
    public OfficeHoursSessionResponse getOfficeHoursSession(
            @PathVariable UUID sessionId,
            @RequestParam UUID viewerUserId
    ) {
        return OfficeHoursSessionResponse.from(
                officeHoursService.findOfficeHoursSession(sessionId, viewerUserId)
        );
    }

    @PostMapping("/office-hours/{sessionId}/waitlist")
    public ResponseEntity<OfficeHoursWaitlistEntryResponse> joinWaitlist(
            @PathVariable UUID sessionId,
            @Valid @RequestBody OfficeHoursLearnerRequest request
    ) {
        OfficeHoursWaitlistEntry entry = officeHoursService.joinWaitlist(sessionId, request.learnerUserId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{learnerUserId}")
                .buildAndExpand(entry.learnerUserId())
                .toUri();

        return ResponseEntity.created(location).body(OfficeHoursWaitlistEntryResponse.from(entry));
    }

    @GetMapping("/office-hours/{sessionId}/waitlist")
    public OfficeHoursWaitlistListResponse listWaitlist(
            @PathVariable UUID sessionId,
            @RequestParam UUID viewerUserId
    ) {
        return OfficeHoursWaitlistListResponse.from(
                officeHoursService.listWaitlist(sessionId, viewerUserId)
        );
    }

    @PostMapping("/office-hours/{sessionId}/admit-next")
    public OfficeHoursWaitlistEntryResponse admitNextLearner(
            @PathVariable UUID sessionId,
            @Valid @RequestBody OfficeHoursActorRequest request
    ) {
        return OfficeHoursWaitlistEntryResponse.from(
                officeHoursService.admitNextLearner(sessionId, request.actorUserId())
        );
    }

    @PostMapping("/office-hours/{sessionId}/voice-join")
    public ResponseEntity<VoicePresenceResponse> joinVoice(
            @PathVariable UUID sessionId,
            @Valid @RequestBody OfficeHoursActorRequest request
    ) {
        VoicePresence presence = officeHoursService.joinVoiceAsManager(sessionId, request.actorUserId());
        return ResponseEntity.ok(VoicePresenceResponse.from(presence));
    }

    @PostMapping("/office-hours/{sessionId}/learner-voice-join")
    public ResponseEntity<VoicePresenceResponse> joinVoiceAsLearner(
            @PathVariable UUID sessionId,
            @Valid @RequestBody OfficeHoursLearnerRequest request
    ) {
        VoicePresence presence = officeHoursService.joinVoiceAsAdmittedLearner(
                sessionId,
                request.learnerUserId()
        );
        return ResponseEntity.ok(VoicePresenceResponse.from(presence));
    }

    @PostMapping("/office-hours/{sessionId}/end")
    public OfficeHoursSessionResponse endSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody OfficeHoursActorRequest request
    ) {
        return OfficeHoursSessionResponse.from(
                officeHoursService.endSession(sessionId, request.actorUserId())
        );
    }
}
