package com.chanter.agent.api;

import com.chanter.agent.domain.AiUsageMetrics;

public record AiUsageMetricsResponse(
        java.util.UUID studyServerId,
        String planTier,
        int totalInvocations,
        int aiInvocationLimit,
        int remainingInvocations,
        boolean quotaExhausted,
        int lowConfidenceHandoffs
) {
    static AiUsageMetricsResponse from(AiUsageMetrics metrics) {
        return new AiUsageMetricsResponse(
                metrics.studyServerId(),
                metrics.planTier(),
                metrics.totalInvocations(),
                metrics.aiInvocationLimit(),
                metrics.remainingInvocations(),
                metrics.quotaExhausted(),
                metrics.lowConfidenceHandoffs()
        );
    }
}
