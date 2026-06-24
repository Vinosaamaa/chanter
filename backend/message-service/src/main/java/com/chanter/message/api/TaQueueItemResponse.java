package com.chanter.message.api;

import com.chanter.message.domain.TaQueueItem;
import java.time.Instant;
import java.util.UUID;

public record TaQueueItemResponse(
        UUID id,
        UUID cohortId,
        UUID courseId,
        UUID channelId,
        UUID supportQuestionId,
        UUID requesterUserId,
        String status,
        UUID pickedUpByUserId,
        UUID resolvedByUserId,
        Instant createdAt,
        Instant pickedUpAt,
        Instant resolvedAt,
        Instant cancelledAt
) {

    public static TaQueueItemResponse from(TaQueueItem item) {
        return new TaQueueItemResponse(
                item.id(),
                item.cohortId(),
                item.courseId(),
                item.channelId(),
                item.supportQuestionId(),
                item.requesterUserId(),
                item.status().name(),
                item.pickedUpByUserId(),
                item.resolvedByUserId(),
                item.createdAt(),
                item.pickedUpAt(),
                item.resolvedAt(),
                item.cancelledAt()
        );
    }
}
