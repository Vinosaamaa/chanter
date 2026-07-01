package com.chanter.message.application;

public record ChannelSummaryMetrics(
        ChannelSummaryMetric questionsAsked,
        ChannelSummaryMetric replies,
        ChannelSummaryMetric views,
        ChannelSummaryMetric resolvedPercent
) {
}
