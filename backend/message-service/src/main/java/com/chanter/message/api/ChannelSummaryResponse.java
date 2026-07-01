package com.chanter.message.api;

import com.chanter.message.application.ChannelSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChannelSummaryResponse(
        UUID channelId,
        String channelName,
        int windowDays,
        Instant windowStart,
        Instant windowEnd,
        Instant generatedAt,
        ChannelSummaryMetricsResponse metrics,
        ChannelSummaryDigestResponse digest,
        List<ChannelSummaryTimelineEventResponse> timeline
) {
    public static ChannelSummaryResponse from(ChannelSummary summary) {
        return new ChannelSummaryResponse(
                summary.channelId(),
                summary.channelName(),
                summary.windowDays(),
                summary.windowStart(),
                summary.windowEnd(),
                summary.generatedAt(),
                ChannelSummaryMetricsResponse.from(summary.metrics()),
                ChannelSummaryDigestResponse.from(summary.digest()),
                summary.timeline().stream().map(ChannelSummaryTimelineEventResponse::from).toList()
        );
    }
}
