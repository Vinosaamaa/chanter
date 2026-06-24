package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import com.chanter.message.domain.TaQueueItem;
import com.chanter.message.domain.TaQueueItemStatus;
import java.time.Clock;
import java.time.Instant;
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
    private final CourseChannelAccessClient courseChannelAccessClient;
    private final Clock clock;

    public TaQueueService(
            TaQueueRepository taQueueRepository,
            SupportQuestionRepository supportQuestionRepository,
            CohortTaQueueAccessClient cohortTaQueueAccessClient,
            CourseChannelAccessClient courseChannelAccessClient,
            Clock clock
    ) {
        this.taQueueRepository = taQueueRepository;
        this.supportQuestionRepository = supportQuestionRepository;
        this.cohortTaQueueAccessClient = cohortTaQueueAccessClient;
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.clock = clock;
    }

    public TaQueueItem addFromSupportQuestion(
            UUID cohortId,
            UUID channelId,
            UUID supportQuestionId,
            UUID actorUserId
    ) {
        CohortTaQueueAccess cohortAccess = cohortTaQueueAccessClient.requireAccess(cohortId, actorUserId);
        if (!cohortAccess.canAddToTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only enrolled learners can add items to the TA Queue");
        }

        CourseChannelAccess channelAccess = courseChannelAccessClient.requireAccess(channelId, actorUserId);
        if (!channelAccess.canPostSupportQuestion()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only enrolled learners can add items to the TA Queue");
        }
        if (!channelAccess.courseId().equals(cohortAccess.courseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Support Question channel does not belong to this Cohort's Course");
        }

        SupportQuestion supportQuestion = supportQuestionRepository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));

        if (!supportQuestion.senderUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Support Question author can add it to the TA Queue");
        }
        if (supportQuestion.status() != SupportQuestionStatus.AI_LOW_CONFIDENCE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only low-confidence Support Questions can be routed to the TA Queue"
            );
        }

        return taQueueRepository.findActiveBySupportQuestionId(supportQuestionId)
                .orElseGet(() -> {
                    Instant now = clock.instant();
                    TaQueueItem item = new TaQueueItem(
                            UUID.randomUUID(),
                            cohortId,
                            cohortAccess.courseId(),
                            channelId,
                            supportQuestionId,
                            actorUserId,
                            TaQueueItemStatus.OPEN,
                            null,
                            null,
                            now,
                            null,
                            null,
                            null
                    );
                    return taQueueRepository.save(item);
                });
    }

    @Transactional(readOnly = true)
    public List<TaQueueItem> listOpenItems(UUID cohortId, UUID viewerUserId) {
        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, viewerUserId);
        if (!access.canManageTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Cohort TAs can view the TA Queue");
        }

        return taQueueRepository.findOpenByCohortId(cohortId);
    }

    public TaQueueItem pickupItem(UUID cohortId, UUID itemId, UUID actorUserId) {
        return transitionItem(
                cohortId,
                itemId,
                actorUserId,
                true,
                TaQueueItemStatus.OPEN,
                TaQueueItemStatus.IN_PROGRESS
        );
    }

    public TaQueueItem resolveItem(UUID cohortId, UUID itemId, UUID actorUserId) {
        return transitionItem(
                cohortId,
                itemId,
                actorUserId,
                true,
                TaQueueItemStatus.IN_PROGRESS,
                TaQueueItemStatus.RESOLVED
        );
    }

    public TaQueueItem cancelItem(UUID cohortId, UUID itemId, UUID actorUserId) {
        TaQueueItem item = requireOpenItem(cohortId, itemId);
        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, actorUserId);
        boolean isRequester = item.requesterUserId().equals(actorUserId);
        if (!isRequester && !access.canManageTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester or a Cohort TA can cancel a queue item");
        }
        if (item.status() != TaQueueItemStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open TA Queue items can be cancelled");
        }

        return applyTransition(item, TaQueueItemStatus.OPEN, TaQueueItemStatus.CANCELLED, actorUserId);
    }

    private TaQueueItem transitionItem(
            UUID cohortId,
            UUID itemId,
            UUID actorUserId,
            boolean requireManageAccess,
            TaQueueItemStatus expectedStatus,
            TaQueueItemStatus newStatus
    ) {
        TaQueueItem item = requireOpenItem(cohortId, itemId);
        CohortTaQueueAccess access = cohortTaQueueAccessClient.requireAccess(cohortId, actorUserId);
        if (requireManageAccess && !access.canManageTaQueue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Cohort TAs can manage the TA Queue");
        }
        if (item.status() != expectedStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TA Queue item is not in the expected state");
        }

        return applyTransition(item, expectedStatus, newStatus, actorUserId);
    }

    private TaQueueItem requireOpenItem(UUID cohortId, UUID itemId) {
        return taQueueRepository.findByIdAndCohortId(itemId, cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TA Queue item not found"));
    }

    private TaQueueItem applyTransition(
            TaQueueItem item,
            TaQueueItemStatus expectedStatus,
            TaQueueItemStatus newStatus,
            UUID actorUserId
    ) {
        boolean updated = taQueueRepository.updateStatus(
                item.id(),
                expectedStatus,
                newStatus,
                actorUserId,
                clock.instant()
        );
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TA Queue item is not in the expected state");
        }

        return taQueueRepository.findByIdAndCohortId(item.id(), item.cohortId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TA Queue item not found"));
    }
}
