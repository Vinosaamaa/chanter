package com.chanter.message.application;

import java.time.Instant;

public record ChannelSummaryTimelineEvent(
        String type,
        String title,
        Instant occurredAt
) {
}
