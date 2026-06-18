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
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Instructors can view unanswered Support Questions");
        }

        return repository.findByChannelIdAndStatus(channelId, SupportQuestionStatus.UNANSWERED);
    }
}
