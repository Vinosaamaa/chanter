package com.chanter.community.domain;

import java.time.Instant;

public record HomeSummaryUpNextItem(
        String id,
        String kind,
        String title,
        String suffix,
        String detail,
        String actionLabel,
        String href,
        Instant startsAt
) {
}
