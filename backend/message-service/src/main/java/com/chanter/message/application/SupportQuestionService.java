package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
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

    public SupportQuestionService(
            SupportQuestionRepository repository,
            CourseChannelAccessClient courseChannelAccessClient,
            SupportQuestionWriter supportQuestionWriter
    ) {
        this.repository = repository;
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.supportQuestionWriter = supportQuestionWriter;
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
    public List<SupportQuestion> listUnansweredSupportQuestions(UUID channelId, UUID viewerUserId) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);

        if (access.canViewUnansweredSupportQuestions()) {
            return repository.findByChannelIdAndStatus(
                    channelId,
                    SupportQuestionStatus.UNANSWERED
            );
        }

        if (access.canPostSupportQuestion()) {
            return repository.findByChannelIdAndSenderUserIdAndStatus(
                    channelId,
                    viewerUserId,
                    SupportQuestionStatus.UNANSWERED
            );
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
}
