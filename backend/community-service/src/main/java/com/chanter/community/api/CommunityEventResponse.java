package com.chanter.community.api;

import com.chanter.community.domain.CommunityEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommunityEventResponse(
        UUID id,
        UUID studyServerId,
        String title,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        Integer capacity,
        String visibility,
        UUID courseId,
        UUID cohortId,
        UUID createdByUserId,
        String status,
        long goingCount,
        long interestedCount,
        String viewerRsvp,
        boolean canEdit,
        String sharePath,
        String calendarPath,
        String icsPath
) {
    static CommunityEventResponse from(CommunityEvent event, boolean canEdit) {
        String sharePath = "/app/servers/" + event.studyServerId() + "/community/events?event=" + event.id();
        String calendarPath = "/app/calendar?event=" + event.id();
        String icsPath = "/api/v1/study-servers/" + event.studyServerId() + "/events/" + event.id() + "/ics";
        return new CommunityEventResponse(
                event.id(),
                event.studyServerId(),
                event.title(),
                event.description(),
                event.location(),
                event.startsAt(),
                event.endsAt(),
                event.capacity(),
                event.visibility().name(),
                event.courseId(),
                event.cohortId(),
                event.createdByUserId(),
                event.status().name(),
                event.goingCount(),
                event.interestedCount(),
                event.viewerRsvp() == null ? null : event.viewerRsvp().name(),
                canEdit,
                sharePath,
                calendarPath,
                icsPath
        );
    }

    public record CommunityEventListResponse(List<CommunityEventResponse> events) {
        static CommunityEventListResponse from(List<CommunityEvent> events, boolean canEdit) {
            return new CommunityEventListResponse(
                    events.stream().map(event -> CommunityEventResponse.from(event, canEdit)).toList()
            );
        }
    }
}
