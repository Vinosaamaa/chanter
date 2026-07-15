package com.chanter.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CalendarItem(
        String id,
        CalendarItemType type,
        String title,
        String contextLabel,
        Instant startsAt,
        Instant endsAt,
        String href,
        String actionLabel,
        String actionKind,
        CommunityEventRsvpStatus viewerRsvp,
        UUID studyServerId,
        UUID courseId,
        UUID cohortId,
        UUID sourceId
) {
}
