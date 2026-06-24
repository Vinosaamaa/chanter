package com.chanter.agent.domain;

import java.util.UUID;

public record AiUsageMetrics(
        UUID studyServerId,
        int totalInvocations,
        int lowConfidenceHandoffs
) {
}
