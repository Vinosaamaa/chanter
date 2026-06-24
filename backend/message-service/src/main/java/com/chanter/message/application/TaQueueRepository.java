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

    Optional<TaQueueItem> findActiveBySupportQuestionId(UUID supportQuestionId);

    List<TaQueueItem> findOpenByCohortId(UUID cohortId);

    boolean updateStatus(
            UUID itemId,
            TaQueueItemStatus expectedStatus,
            TaQueueItemStatus newStatus,
            UUID actorUserId,
            Instant timestamp
    );
}
