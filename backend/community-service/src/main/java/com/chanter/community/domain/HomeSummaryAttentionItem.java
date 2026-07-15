package com.chanter.community.domain;

import java.time.Instant;

public record HomeSummaryAttentionItem(
        String id,
        String kind,
        String headline,
        String suffix,
        boolean suffixOnNewLine,
        String actionLabel,
        String actionVariant,
        String href,
        Instant startsAt
) {
}
