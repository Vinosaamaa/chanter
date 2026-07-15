package com.chanter.community.api;

import com.chanter.community.domain.CalendarAggregate;
import com.chanter.community.domain.CalendarItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CalendarResponse(
        List<CalendarItemResponse> items,
        List<String> notes
) {
    public static CalendarResponse from(CalendarAggregate aggregate) {
        return new CalendarResponse(
                aggregate.items().stream().map(CalendarItemResponse::from).toList(),
                aggregate.notes()
        );
    }

    public record CalendarItemResponse(
            String id,
            String type,
            String title,
            String contextLabel,
            Instant startsAt,
            Instant endsAt,
            String href,
            String actionLabel,
            String actionKind,
            String viewerRsvp,
            UUID studyServerId,
            UUID courseId,
            UUID cohortId,
            UUID sourceId
    ) {
        static CalendarItemResponse from(CalendarItem item) {
            return new CalendarItemResponse(
                    item.id(),
                    item.type().name(),
                    item.title(),
                    item.contextLabel(),
                    item.startsAt(),
                    item.endsAt(),
                    item.href(),
                    item.actionLabel(),
                    item.actionKind(),
                    item.viewerRsvp() == null ? null : item.viewerRsvp().name(),
                    item.studyServerId(),
                    item.courseId(),
                    item.cohortId(),
                    item.sourceId()
            );
        }
    }
}
