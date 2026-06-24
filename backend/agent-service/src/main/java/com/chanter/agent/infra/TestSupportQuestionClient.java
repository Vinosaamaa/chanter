package com.chanter.agent.infra;

import com.chanter.agent.application.SupportQuestionClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestSupportQuestionClient implements SupportQuestionClient {

    private final Map<UUID, SupportQuestion> supportQuestions = new ConcurrentHashMap<>();

    public void registerSupportQuestion(SupportQuestion supportQuestion) {
        supportQuestions.put(supportQuestion.id(), supportQuestion);
    }

    public void clear() {
        supportQuestions.clear();
    }

    @Override
    public SupportQuestion getSupportQuestion(UUID channelId, UUID supportQuestionId, UUID viewerUserId) {
        SupportQuestion supportQuestion = supportQuestions.get(supportQuestionId);
        if (supportQuestion == null || !supportQuestion.channelId().equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Support Question not found");
        }
        if (!supportQuestion.senderUserId().equals(viewerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support Question access denied");
        }
        return supportQuestion;
    }

    @Override
    public SupportQuestion updateStatus(
            UUID channelId,
            UUID supportQuestionId,
            UUID actorUserId,
            String status
    ) {
        SupportQuestion supportQuestion = getSupportQuestion(channelId, supportQuestionId, actorUserId);
        if (!"AI_ANSWERED".equals(status) && !"AI_LOW_CONFIDENCE".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assistant outcomes must be AI_ANSWERED or AI_LOW_CONFIDENCE"
            );
        }

        if (!"UNANSWERED".equals(supportQuestion.status()) && !status.equals(supportQuestion.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is no longer unanswered");
        }

        SupportQuestion updated = new SupportQuestion(
                supportQuestion.id(),
                supportQuestion.channelId(),
                supportQuestion.senderUserId(),
                supportQuestion.body(),
                status,
                supportQuestion.createdAt()
        );
        supportQuestions.put(supportQuestionId, updated);
        return updated;
    }

    public static SupportQuestion unanswered(
            UUID supportQuestionId,
            UUID channelId,
            UUID senderUserId,
            String body
    ) {
        return new SupportQuestion(
                supportQuestionId,
                channelId,
                senderUserId,
                body,
                "UNANSWERED",
                Instant.parse("2026-06-23T00:00:00Z")
        );
    }
}
