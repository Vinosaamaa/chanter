package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record TaQueueItem(
        UUID id,
        UUID cohortId,
        UUID courseId,
        UUID channelId,
        UUID supportQuestionId,
        UUID requesterUserId,
        TaQueueItemStatus status,
        UUID pickedUpByUserId,
        UUID resolvedByUserId,
        Instant createdAt,
        Instant pickedUpAt,
        Instant resolvedAt,
        Instant cancelledAt
) {
}
