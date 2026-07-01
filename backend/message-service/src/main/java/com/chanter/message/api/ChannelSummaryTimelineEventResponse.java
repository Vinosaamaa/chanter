package com.chanter.message.api;

import com.chanter.message.application.ChannelSummaryTimelineEvent;
import java.time.Instant;

public record ChannelSummaryTimelineEventResponse(
        String type,
        String title,
        Instant occurredAt
) {
    public static ChannelSummaryTimelineEventResponse from(ChannelSummaryTimelineEvent event) {
        return new ChannelSummaryTimelineEventResponse(event.type(), event.title(), event.occurredAt());
    }
}
