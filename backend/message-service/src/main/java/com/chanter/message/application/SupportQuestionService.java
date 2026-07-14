package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionReply;
import com.chanter.message.domain.SupportQuestionStatus;
import com.chanter.message.domain.TaQueueItemStatus;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportQuestionService {

    private final SupportQuestionRepository repository;
    private final CourseChannelAccessClient courseChannelAccessClient;
    private final SupportQuestionWriter supportQuestionWriter;
    private final SupportQuestionReplyRepository replyRepository;
    private final TaQueueRepository taQueueRepository;
    private final Clock clock;

    public SupportQuestionService(
            SupportQuestionRepository repository,
            CourseChannelAccessClient courseChannelAccessClient,
            SupportQuestionWriter supportQuestionWriter,
            SupportQuestionReplyRepository replyRepository,
            TaQueueRepository taQueueRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.supportQuestionWriter = supportQuestionWriter;
        this.replyRepository = replyRepository;
        this.taQueueRepository = taQueueRepository;
        this.clock = clock;
    }

    public SupportQuestion postSupportQuestion(
            UUID channelId,
            UUID senderUserId,
            String body,
            String idempotencyKey
    ) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, senderUserId);
        if (!access.canPostSupportQuestion()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only enrolled learners can post Support Questions");
        }

        String normalizedBody = body.trim();
        if (normalizedBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Support Question body must not be blank");
        }

        return repository.findByChannelSenderAndIdempotencyKey(channelId, senderUserId, idempotencyKey)
                .orElseGet(() -> supportQuestionWriter.createIfAbsent(
                        channelId,
                        senderUserId,
                        normalizedBody,
                        idempotencyKey
                ));
    }

    @Transactional(readOnly = true)
    public List<SupportQuestion> listSupportQuestions(UUID channelId, UUID viewerUserId) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);

        if (access.canViewUnansweredSupportQuestions()) {
            return repository.findByChannelId(channelId);
        }

        if (access.canPostSupportQuestion()) {
            return repository.findByChannelIdAndSenderUserId(channelId, viewerUserId);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support Question list access denied");
    }

    @Transactional(readOnly = true)
    public SupportQuestion getSupportQuestion(UUID channelId, UUID supportQuestionId, UUID viewerUserId) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);
        SupportQuestion supportQuestion = repository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));

        if (supportQuestion.senderUserId().equals(viewerUserId)) {
            return supportQuestion;
        }
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support Question access requires sender or Instructor role");
        }

        return supportQuestion;
    }

    public SupportQuestion updateSupportQuestionStatus(
            UUID channelId,
            UUID supportQuestionId,
            UUID actorUserId,
            SupportQuestionStatus status
    ) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, actorUserId);
        if (!access.canPostSupportQuestion()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only enrolled learners can update Support Question status");
        }

        SupportQuestion supportQuestion = repository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));

        if (!supportQuestion.senderUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Support Question author can record assistant outcomes");
        }

        if (supportQuestion.status() != SupportQuestionStatus.UNANSWERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is no longer unanswered");
        }

        if (status != SupportQuestionStatus.AI_ANSWERED && status != SupportQuestionStatus.AI_LOW_CONFIDENCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant outcomes must be AI_ANSWERED or AI_LOW_CONFIDENCE");
        }

        boolean updated = repository.updateStatus(
                supportQuestionId,
                SupportQuestionStatus.UNANSWERED,
                status
        );
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is no longer unanswered");
        }

        return repository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));
    }

    @Transactional
    public SupportQuestionReply postReply(
            UUID channelId,
            UUID supportQuestionId,
            UUID authorUserId,
            String body
    ) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, authorUserId);
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Course Instructors and TAs can reply");
        }

        SupportQuestion supportQuestion = repository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));
        String normalizedBody = body.trim();
        if (normalizedBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply body must not be blank");
        }

        if (isClosed(supportQuestion.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is already closed");
        }
        lockQuestionForReply(channelId, supportQuestion);

        return replyRepository.save(new SupportQuestionReply(
                UUID.randomUUID(),
                supportQuestionId,
                authorUserId,
                normalizedBody,
                clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public List<SupportQuestionReply> listReplies(
            UUID channelId,
            UUID supportQuestionId,
            UUID viewerUserId
    ) {
        getSupportQuestion(channelId, supportQuestionId, viewerUserId);
        return replyRepository.findBySupportQuestionId(supportQuestionId);
    }

    @Transactional
    public SupportQuestion moderateSupportQuestion(
            UUID channelId,
            UUID supportQuestionId,
            UUID actorUserId,
            SupportQuestionStatus status
    ) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, actorUserId);
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Course Instructors and TAs can moderate questions");
        }
        if (status != SupportQuestionStatus.RESOLVED
                && status != SupportQuestionStatus.CANCELLED
                && status != SupportQuestionStatus.DUPLICATE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Moderation status must close the Support Question");
        }

        SupportQuestion supportQuestion = repository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));
        if (supportQuestion.status() == status) {
            return supportQuestion;
        }
        if (isClosed(supportQuestion.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is already closed");
        }

        boolean updated = repository.updateStatus(supportQuestionId, supportQuestion.status(), status);
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question status has changed");
        }
        taQueueRepository.closeActiveBySupportQuestionId(
                supportQuestionId,
                status == SupportQuestionStatus.RESOLVED
                        ? TaQueueItemStatus.RESOLVED
                        : TaQueueItemStatus.CANCELLED,
                clock.instant()
        );
        return repository.findByIdAndChannelId(channelId, supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found"));
    }

    private void lockQuestionForReply(UUID channelId, SupportQuestion supportQuestion) {
        boolean locked = repository.updateStatus(
                supportQuestion.id(),
                supportQuestion.status(),
                SupportQuestionStatus.HUMAN_ANSWERED
        );
        if (locked) {
            return;
        }

        SupportQuestion latest = repository.findByIdAndChannelId(channelId, supportQuestion.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Support Question not found"
                ));
        if (latest.status() != SupportQuestionStatus.HUMAN_ANSWERED
                || !repository.updateStatus(
                        latest.id(),
                        SupportQuestionStatus.HUMAN_ANSWERED,
                        SupportQuestionStatus.HUMAN_ANSWERED
                )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question status has changed");
        }
    }

    private static boolean isClosed(SupportQuestionStatus status) {
        return status == SupportQuestionStatus.RESOLVED
                || status == SupportQuestionStatus.CANCELLED
                || status == SupportQuestionStatus.DUPLICATE;
    }
}
