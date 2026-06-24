package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import com.chanter.message.domain.TaQueueItem;
import com.chanter.message.domain.TaQueueItemStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaQueueService {

    private final TaQueueRepository taQueueRepository;
    private final SupportQuestionRepository supportQuestionRepository;
    private final CohortTaQueueAccessClient cohortTaQueueAccessClient;
    private final Clock clock;

    public TaQueueService(
            TaQueueRepository taQueueRepository,
            SupportQuestionRepository supportQuestionRepository,
            CohortTaQueueAccessClient cohortTaQueueAccessClient,
            Clock clock
    ) {
        this.taQueueRepository = taQueueRepository;
        this.supportQuestionRepository = supportQuestionRepository;
        this.cohortTaQueueAccessClient = cohortTaQueueAccessClient;
        this.clock = clock;
    }

    @Transactional
    public TaQueueItem addToQueue(
            UUID cohortId,
            UUID learnerUserId,
            UUID supportQuestionId,
            UUID channelId
    ) {
        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, learnerUserId);
        if (!access.canAddToTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only enrolled learners can add items to the TA Queue");
        }

        SupportQuestion supportQuestion = supportQuestionRepository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Support Question not found in Course Channel"));

        if (!supportQuestion.senderUserId().equals(learnerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Learners can only queue their own Support Questions");
        }
        if (supportQuestion.status() != SupportQuestionStatus.AI_LOW_CONFIDENCE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only low-confidence Support Questions can be added to the TA Queue"
            );
        }
        if (taQueueRepository.existsActiveBySupportQuestionId(supportQuestionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is already in the TA Queue");
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        TaQueueItem item = new TaQueueItem(
                UUID.randomUUID(),
                cohortId,
                supportQuestionId,
                channelId,
                learnerUserId,
                supportQuestion.body(),
                TaQueueItemStatus.OPEN,
                null,
                now,
                now
        );
        return taQueueRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<TaQueueItem> listQueue(UUID cohortId, UUID viewerUserId) {
        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, viewerUserId);
        if (!access.canManageTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Cohort TAs can view the TA Queue");
        }

        return taQueueRepository.findByCohortIdAndStatuses(
                cohortId,
                List.of(TaQueueItemStatus.OPEN, TaQueueItemStatus.PICKED_UP)
        );
    }

    @Transactional
    public TaQueueItem pickupQueueItem(UUID cohortId, UUID itemId, UUID taUserId) {
        return transitionQueueItem(
                cohortId,
                itemId,
                taUserId,
                TaQueueItemStatus.OPEN,
                TaQueueItemStatus.PICKED_UP,
                taUserId
        );
    }

    @Transactional
    public TaQueueItem resolveQueueItem(UUID cohortId, UUID itemId, UUID taUserId) {
        TaQueueItem existing = requireManageableItem(cohortId, itemId, taUserId);
        TaQueueItemStatus fromStatus = existing.status() == TaQueueItemStatus.OPEN
                ? TaQueueItemStatus.OPEN
                : TaQueueItemStatus.PICKED_UP;
        return transitionQueueItem(cohortId, itemId, taUserId, fromStatus, TaQueueItemStatus.RESOLVED, taUserId);
    }

    @Transactional
    public TaQueueItem cancelQueueItem(UUID cohortId, UUID itemId, UUID actorUserId) {
        TaQueueItem existing = taQueueRepository.findByIdAndCohortId(itemId, cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TA Queue item not found"));

        if (existing.status() != TaQueueItemStatus.OPEN && existing.status() != TaQueueItemStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TA Queue item is already closed");
        }

        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, actorUserId);
        boolean isLearnerOwner = existing.learnerUserId().equals(actorUserId) && access.canAddToTaQueue();
        boolean isTa = access.canManageTaQueue();
        if (!isLearnerOwner && !isTa) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the learner or a Cohort TA can cancel TA Queue items");
        }

        Instant updatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean updated = taQueueRepository.updateStatus(
                itemId,
                cohortId,
                existing.status(),
                TaQueueItemStatus.CANCELLED,
                existing.assignedTaUserId(),
                updatedAt
        );
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TA Queue item status has changed");
        }

        return taQueueRepository.findByIdAndCohortId(itemId, cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TA Queue item not found"));
    }

    private TaQueueItem requireManageableItem(UUID cohortId, UUID itemId, UUID taUserId) {
        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, taUserId);
        if (!access.canManageTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Cohort TAs can manage the TA Queue");
        }

        return taQueueRepository.findByIdAndCohortId(itemId, cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TA Queue item not found"));
    }

    private TaQueueItem transitionQueueItem(
            UUID cohortId,
            UUID itemId,
            UUID taUserId,
            TaQueueItemStatus fromStatus,
            TaQueueItemStatus toStatus,
            UUID assignedTaUserId
    ) {
        requireManageableItem(cohortId, itemId, taUserId);

        Instant updatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean updated = taQueueRepository.updateStatus(
                itemId,
                cohortId,
                fromStatus,
                toStatus,
                assignedTaUserId,
                updatedAt
        );
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TA Queue item status has changed");
        }

        return taQueueRepository.findByIdAndCohortId(itemId, cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TA Queue item not found"));
    }
}
