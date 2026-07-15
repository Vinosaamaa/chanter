package com.chanter.community.application;

import com.chanter.community.domain.CommunityEvent;
import com.chanter.community.domain.CommunityEventFilter;
import com.chanter.community.domain.CommunityEventRsvpStatus;
import com.chanter.community.domain.CommunityEventStatus;
import com.chanter.community.domain.CommunityEventVisibility;
import com.chanter.community.domain.StudyServer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityEventService {

    private final CommunityEventRepository eventRepository;
    private final StudyServerRepository studyServerRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public CommunityEventService(
            CommunityEventRepository eventRepository,
            StudyServerRepository studyServerRepository,
            CourseRepository courseRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.studyServerRepository = studyServerRepository;
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommunityEvent> listEvents(UUID studyServerId, UUID viewerUserId, CommunityEventFilter filter) {
        requireStudyServerMember(studyServerId, viewerUserId);
        requireStudyServerExists(studyServerId);
        return eventRepository.findVisibleEvents(studyServerId, viewerUserId, filter, clock.instant());
    }

    @Transactional(readOnly = true)
    public CommunityEvent getEvent(UUID studyServerId, UUID eventId, UUID viewerUserId) {
        requireStudyServerMember(studyServerId, viewerUserId);
        CommunityEvent event = requireEventOnServer(studyServerId, eventId, viewerUserId);
        requireViewerCanSee(event, viewerUserId);
        return event;
    }

    @Transactional
    public CommunityEvent createEvent(
            UUID studyServerId,
            UUID actorUserId,
            String title,
            String description,
            String location,
            Instant startsAt,
            Instant endsAt,
            Integer capacity,
            CommunityEventVisibility visibility,
            UUID courseId,
            UUID cohortId
    ) {
        requireStudyServerOwner(studyServerId, actorUserId);
        validateSchedule(startsAt, endsAt);
        validateVisibility(studyServerId, visibility, courseId, cohortId);

        Instant now = clock.instant();
        CommunityEvent event = new CommunityEvent(
                UUID.randomUUID(),
                studyServerId,
                title.trim(),
                blankToNull(description),
                blankToNull(location),
                startsAt,
                endsAt,
                capacity,
                visibility,
                courseId,
                cohortId,
                actorUserId,
                CommunityEventStatus.SCHEDULED,
                now,
                now,
                0,
                0,
                null
        );
        return eventRepository.save(event);
    }

    @Transactional
    public CommunityEvent updateEvent(
            UUID studyServerId,
            UUID eventId,
            UUID actorUserId,
            String title,
            String description,
            String location,
            Instant startsAt,
            Instant endsAt,
            Integer capacity,
            CommunityEventVisibility visibility,
            UUID courseId,
            UUID cohortId
    ) {
        requireStudyServerOwner(studyServerId, actorUserId);
        CommunityEvent existing = requireEventOnServer(studyServerId, eventId, actorUserId);
        if (existing.cancelled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled events cannot be edited");
        }
        validateSchedule(startsAt, endsAt);
        validateVisibility(studyServerId, visibility, courseId, cohortId);

        CommunityEvent updated = new CommunityEvent(
                existing.id(),
                existing.studyServerId(),
                title.trim(),
                blankToNull(description),
                blankToNull(location),
                startsAt,
                endsAt,
                capacity,
                visibility,
                courseId,
                cohortId,
                existing.createdByUserId(),
                existing.status(),
                existing.createdAt(),
                clock.instant(),
                existing.goingCount(),
                existing.interestedCount(),
                existing.viewerRsvp()
        );
        return eventRepository.update(updated);
    }

    @Transactional
    public CommunityEvent cancelEvent(UUID studyServerId, UUID eventId, UUID actorUserId) {
        requireStudyServerOwner(studyServerId, actorUserId);
        CommunityEvent existing = requireEventOnServer(studyServerId, eventId, actorUserId);
        if (existing.cancelled()) {
            return existing;
        }
        eventRepository.setStatus(eventId, CommunityEventStatus.CANCELLED, clock.instant());
        return eventRepository.findById(eventId, actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    @Transactional
    public CommunityEvent upsertRsvp(
            UUID studyServerId,
            UUID eventId,
            UUID actorUserId,
            CommunityEventRsvpStatus status
    ) {
        requireStudyServerMember(studyServerId, actorUserId);
        CommunityEvent event = requireEventOnServer(studyServerId, eventId, actorUserId);
        requireViewerCanSee(event, actorUserId);
        if (event.cancelled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot RSVP to a cancelled event");
        }
        if (status == CommunityEventRsvpStatus.GOING
                && event.capacity() != null
                && event.goingCount() >= event.capacity()
                && event.viewerRsvp() != CommunityEventRsvpStatus.GOING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is at capacity");
        }
        eventRepository.upsertRsvp(eventId, actorUserId, status, clock.instant());
        return eventRepository.findById(eventId, actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private void validateSchedule(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event schedule is required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event end must be after start");
        }
    }

    private void validateVisibility(
            UUID studyServerId,
            CommunityEventVisibility visibility,
            UUID courseId,
            UUID cohortId
    ) {
        switch (visibility) {
            case HUB -> {
                if (courseId != null || cohortId != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Hub events cannot target a Course or Cohort"
                    );
                }
            }
            case COURSE -> {
                if (courseId == null || cohortId != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Course events require a courseId and no cohortId"
                    );
                }
                if (!eventRepository.courseBelongsToStudyServer(courseId, studyServerId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is not on this Study Server");
                }
            }
            case COHORT -> {
                if (courseId == null || cohortId == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Cohort events require courseId and cohortId"
                    );
                }
                if (!eventRepository.courseBelongsToStudyServer(courseId, studyServerId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is not on this Study Server");
                }
                if (!eventRepository.cohortBelongsToCourse(cohortId, courseId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cohort does not belong to Course");
                }
            }
        }
    }

    private void requireViewerCanSee(CommunityEvent event, UUID viewerUserId) {
        boolean visible = switch (event.visibility()) {
            case HUB -> true;
            case COURSE -> event.courseId() != null
                    && (courseRepository.isStudyServerOwner(event.studyServerId(), viewerUserId)
                    || eventRepository.isCourseAccessible(event.courseId(), viewerUserId));
            case COHORT -> event.cohortId() != null
                    && (courseRepository.isStudyServerOwner(event.studyServerId(), viewerUserId)
                    || eventRepository.isCohortAccessible(event.cohortId(), viewerUserId));
        };
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Event is not visible to this member");
        }
    }

    private CommunityEvent requireEventOnServer(UUID studyServerId, UUID eventId, UUID viewerUserId) {
        CommunityEvent event = eventRepository.findById(eventId, viewerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (!event.studyServerId().equals(studyServerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        return event;
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
        StudyServer studyServer = studyServerRepository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
        if (!studyServer.ownerRole().userId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the Study Server owner can manage Community events"
            );
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
