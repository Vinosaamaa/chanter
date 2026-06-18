package com.chanter.message.api;

import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Instant;
import java.util.UUID;

public record SupportQuestionSummaryResponse(
        UUID id,
        UUID channelMessageId,
        UUID channelId,
        UUID senderUserId,
        String body,
        String status,
        Instant createdAt
) {

    public static SupportQuestionSummaryResponse from(SupportQuestion supportQuestion) {
        return new SupportQuestionSummaryResponse(
                supportQuestion.id(),
                supportQuestion.channelMessageId(),
                supportQuestion.channelId(),
                supportQuestion.senderUserId(),
                supportQuestion.body(),
                supportQuestion.status().name(),
                supportQuestion.createdAt()
        );
    }
}
