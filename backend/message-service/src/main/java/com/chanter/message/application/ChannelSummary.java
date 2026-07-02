package com.chanter.message.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChannelSummary(
        UUID channelId,
        String channelName,
        int windowDays,
        Instant windowStart,
        Instant windowEnd,
        Instant generatedAt,
        ChannelSummaryMetrics metrics,
        ChannelSummaryDigest digest,
        List<ChannelSummaryTimelineEvent> timeline
) {
}
