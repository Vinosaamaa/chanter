package com.chanter.agent.domain;

public record AiInvocationCounts(
        int totalInvocations,
        int lowConfidenceHandoffs
) {
}
