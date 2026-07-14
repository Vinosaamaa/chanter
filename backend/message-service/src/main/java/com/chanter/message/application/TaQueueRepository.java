package com.chanter.message.application;

import com.chanter.message.domain.TaQueueItem;
import com.chanter.message.domain.TaQueueItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaQueueRepository {

    TaQueueItem save(TaQueueItem item);

    Optional<TaQueueItem> findByIdAndCohortId(UUID itemId, UUID cohortId);

    List<TaQueueItem> findByCohortIdAndStatuses(UUID cohortId, List<TaQueueItemStatus> statuses);

    boolean existsActiveBySupportQuestionId(UUID supportQuestionId);

    int closeActiveBySupportQuestionId(
            UUID supportQuestionId,
            TaQueueItemStatus status,
            Instant updatedAt
    );

    boolean updateStatus(
            UUID itemId,
            UUID cohortId,
            TaQueueItemStatus fromStatus,
            TaQueueItemStatus toStatus,
            UUID assignedTaUserId,
            Instant updatedAt
    );
}
