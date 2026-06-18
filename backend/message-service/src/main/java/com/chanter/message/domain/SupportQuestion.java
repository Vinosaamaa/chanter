package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record SupportQuestion(
        UUID id,
        UUID channelMessageId,
        UUID channelId,
        UUID senderUserId,
        String body,
        SupportQuestionStatus status,
        String idempotencyKey,
        Instant createdAt
) {
}
