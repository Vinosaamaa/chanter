package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.CommunityEventService;
import com.chanter.community.application.CourseRepository;
import com.chanter.community.domain.CommunityEvent;
import com.chanter.community.domain.CommunityEventFilter;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-servers/{studyServerId}/events")
public class CommunityEventController {

    private static final DateTimeFormatter ICS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final CommunityEventService communityEventService;
    private final CourseRepository courseRepository;

    public CommunityEventController(
            CommunityEventService communityEventService,
            CourseRepository courseRepository
    ) {
        this.communityEventService = communityEventService;
        this.courseRepository = courseRepository;
    }

    @GetMapping
    public CommunityEventResponse.CommunityEventListResponse listEvents(
            @PathVariable UUID studyServerId,
            @RequestParam(defaultValue = "UPCOMING") String filter,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        boolean canEdit = courseRepository.isStudyServerOwner(studyServerId, viewerUserId);
        return CommunityEventResponse.CommunityEventListResponse.from(
                communityEventService.listEvents(
                        studyServerId,
                        viewerUserId,
                        CommunityEventFilter.fromApiValue(filter)
                ),
                canEdit
        );
    }

    @GetMapping("/{eventId}")
    public CommunityEventResponse getEvent(
            @PathVariable UUID studyServerId,
            @PathVariable UUID eventId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        CommunityEvent event = communityEventService.getEvent(studyServerId, eventId, viewerUserId);
        boolean canEdit = courseRepository.isStudyServerOwner(studyServerId, viewerUserId);
        return CommunityEventResponse.from(event, canEdit);
    }

    @PostMapping
    public ResponseEntity<CommunityEventResponse> createEvent(
            @PathVariable UUID studyServerId,
            @Valid @RequestBody CreateCommunityEventRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityEvent event = communityEventService.createEvent(
                studyServerId,
                actorUserId,
                request.title(),
                request.description(),
                request.location(),
                request.startsAt(),
                request.endsAt(),
                request.capacity(),
                request.parsedVisibility(),
                request.courseId(),
                request.cohortId()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{eventId}")
                .buildAndExpand(event.id())
                .toUri();
        return ResponseEntity.created(location).body(CommunityEventResponse.from(event, true));
    }

    @PatchMapping("/{eventId}")
    public CommunityEventResponse updateEvent(
            @PathVariable UUID studyServerId,
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateCommunityEventRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityEvent event = communityEventService.updateEvent(
                studyServerId,
                eventId,
                actorUserId,
                request.title(),
                request.description(),
                request.location(),
                request.startsAt(),
                request.endsAt(),
                request.capacity(),
                request.parsedVisibility(),
                request.courseId(),
                request.cohortId()
        );
        return CommunityEventResponse.from(event, true);
    }

    @PostMapping("/{eventId}/cancel")
    public CommunityEventResponse cancelEvent(
            @PathVariable UUID studyServerId,
            @PathVariable UUID eventId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return CommunityEventResponse.from(
                communityEventService.cancelEvent(studyServerId, eventId, actorUserId),
                true
        );
    }

    @PutMapping("/{eventId}/rsvp")
    public CommunityEventResponse upsertRsvp(
            @PathVariable UUID studyServerId,
            @PathVariable UUID eventId,
            @Valid @RequestBody UpsertCommunityEventRsvpRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CommunityEvent event = communityEventService.upsertRsvp(
                studyServerId,
                eventId,
                actorUserId,
                request.parsedStatus()
        );
        boolean canEdit = courseRepository.isStudyServerOwner(studyServerId, actorUserId);
        return CommunityEventResponse.from(event, canEdit);
    }

    @GetMapping(value = "/{eventId}/ics", produces = "text/calendar")
    public ResponseEntity<byte[]> exportIcs(
            @PathVariable UUID studyServerId,
            @PathVariable UUID eventId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        CommunityEvent event = communityEventService.getEvent(studyServerId, eventId, viewerUserId);
        String ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Chanter//Community Events//EN
                BEGIN:VEVENT
                UID:%s@chanter
                DTSTAMP:%s
                DTSTART:%s
                DTEND:%s
                SUMMARY:%s
                DESCRIPTION:%s
                LOCATION:%s
                END:VEVENT
                END:VCALENDAR
                """.formatted(
                event.id(),
                ICS_FORMAT.format(event.updatedAt()),
                ICS_FORMAT.format(event.startsAt()),
                ICS_FORMAT.format(event.endsAt()),
                escapeIcs(event.title()),
                escapeIcs(event.description() == null ? "" : event.description()),
                escapeIcs(event.location() == null ? "" : event.location())
        ).replace("\n", "\r\n");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chanter-event.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar"))
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeIcs(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}
