package com.chanter.message.application;

public record ChannelSummaryMetric(
        long value,
        int deltaPercent
) {
}
