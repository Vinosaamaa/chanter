package com.chanter.message.api;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Instant;
import java.util.UUID;

public record SupportQuestionResponse(
        UUID id,
        UUID channelMessageId,
        UUID channelId,
        UUID senderUserId,
        String body,
        String status,
        String idempotencyKey,
        Instant createdAt
) {

    public static SupportQuestionResponse from(SupportQuestion supportQuestion) {
        return new SupportQuestionResponse(
                supportQuestion.id(),
                supportQuestion.channelMessageId(),
                supportQuestion.channelId(),
                supportQuestion.senderUserId(),
                supportQuestion.body(),
                supportQuestion.status().name(),
                supportQuestion.idempotencyKey(),
                supportQuestion.createdAt()
        );
    }
}
