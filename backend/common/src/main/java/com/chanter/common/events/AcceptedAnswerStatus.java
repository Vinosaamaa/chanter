package com.chanter.common.events;

import java.util.Set;
import java.util.UUID;

/** IDs-only reconciliation of an answer already authorized and committed by the agent service. */
public record AcceptedAnswerStatus(UUID answerId, UUID questionId, UUID channelId, UUID authorId, String status) {
    public static final String KIND = "ACCEPTED_ANSWER";
    public AcceptedAnswerStatus {
        if (answerId == null || questionId == null || channelId == null || authorId == null || status == null
                || !Set.of("AI_ANSWERED", "AI_LOW_CONFIDENCE").contains(status)) {
            throw new IllegalArgumentException("Invalid accepted answer status");
        }
    }
    public String aggregateKey() { return KIND + ":" + questionId; }
}
