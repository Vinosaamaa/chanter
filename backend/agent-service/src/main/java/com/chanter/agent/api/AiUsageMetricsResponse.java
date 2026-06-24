package com.chanter.agent.api;

import com.chanter.agent.domain.AiUsageMetrics;

public record AiUsageMetricsResponse(
        java.util.UUID studyServerId,
        int totalInvocations,
        int lowConfidenceHandoffs
) {
    static AiUsageMetricsResponse from(AiUsageMetrics metrics) {
        return new AiUsageMetricsResponse(
                metrics.studyServerId(),
                metrics.totalInvocations(),
                metrics.lowConfidenceHandoffs()
        );
    }
}
