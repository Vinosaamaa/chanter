package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record SupportQuestionReply(
        UUID id,
        UUID supportQuestionId,
        UUID authorUserId,
        String body,
        Instant createdAt
) {
}
