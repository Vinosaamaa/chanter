package com.chanter.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record StudyAssistantAnswerAudit(
        UUID answerId,
        String invocationType,
        int sourceCount,
        boolean llmUsed,
        String llmProvider,
        String llmModel,
        Instant createdAt
) {
}
