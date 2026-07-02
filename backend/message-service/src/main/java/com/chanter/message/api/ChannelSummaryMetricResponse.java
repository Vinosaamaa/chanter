package com.chanter.message.api;

import com.chanter.message.application.ChannelSummaryMetric;

public record ChannelSummaryMetricResponse(
        long value,
        int deltaPercent
) {
    public static ChannelSummaryMetricResponse from(ChannelSummaryMetric metric) {
        return new ChannelSummaryMetricResponse(metric.value(), metric.deltaPercent());
    }
}
