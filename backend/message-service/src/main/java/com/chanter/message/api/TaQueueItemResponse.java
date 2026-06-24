package com.chanter.message.api;

import com.chanter.message.domain.TaQueueItem;
import java.time.Instant;
import java.util.UUID;

public record TaQueueItemResponse(
        UUID id,
        UUID cohortId,
        UUID supportQuestionId,
        UUID channelId,
        UUID learnerUserId,
        String body,
        String status,
        UUID assignedTaUserId,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaQueueItemResponse from(TaQueueItem item) {
        return new TaQueueItemResponse(
                item.id(),
                item.cohortId(),
                item.supportQuestionId(),
                item.channelId(),
                item.learnerUserId(),
                item.body(),
                item.status().name(),
                item.assignedTaUserId(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
