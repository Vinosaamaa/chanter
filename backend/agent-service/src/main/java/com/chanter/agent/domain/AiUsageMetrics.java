package com.chanter.agent.domain;

import java.util.UUID;

public record AiUsageMetrics(
        UUID studyServerId,
        String planTier,
        int totalInvocations,
        int aiInvocationLimit,
        int lowConfidenceHandoffs
) {

    public boolean quotaExhausted() {
        return totalInvocations >= aiInvocationLimit;
    }

    public int remainingInvocations() {
        return Math.max(0, aiInvocationLimit - totalInvocations);
    }
}
