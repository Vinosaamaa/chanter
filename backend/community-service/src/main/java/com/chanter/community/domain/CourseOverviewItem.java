package com.chanter.community.domain;

import java.time.Instant;

public record CourseOverviewItem(
        String id,
        String kind,
        String title,
        String detail,
        String actionLabel,
        String href,
        Instant startsAt
) {
}
