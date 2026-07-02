package com.chanter.message.api;

import com.chanter.message.application.ChannelSummaryMetrics;
import java.util.List;

public record ChannelSummaryMetricsResponse(
        ChannelSummaryMetricResponse questionsAsked,
        ChannelSummaryMetricResponse replies,
        ChannelSummaryMetricResponse views,
        ChannelSummaryMetricResponse resolvedPercent
) {
    public static ChannelSummaryMetricsResponse from(ChannelSummaryMetrics metrics) {
        return new ChannelSummaryMetricsResponse(
                ChannelSummaryMetricResponse.from(metrics.questionsAsked()),
                ChannelSummaryMetricResponse.from(metrics.replies()),
                ChannelSummaryMetricResponse.from(metrics.views()),
                ChannelSummaryMetricResponse.from(metrics.resolvedPercent())
        );
    }
}
