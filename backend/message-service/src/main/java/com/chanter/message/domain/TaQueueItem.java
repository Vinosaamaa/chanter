package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record TaQueueItem(
        UUID id,
        UUID cohortId,
        UUID supportQuestionId,
        UUID channelId,
        UUID learnerUserId,
        String body,
        TaQueueItemStatus status,
        UUID assignedTaUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
