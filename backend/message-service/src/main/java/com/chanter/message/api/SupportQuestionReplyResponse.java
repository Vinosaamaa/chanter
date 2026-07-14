package com.chanter.message.api;

import com.chanter.message.domain.SupportQuestionReply;
import java.time.Instant;
import java.util.UUID;

public record SupportQuestionReplyResponse(
        UUID id,
        UUID supportQuestionId,
        UUID authorUserId,
        String body,
        Instant createdAt
) {
    public static SupportQuestionReplyResponse from(SupportQuestionReply reply) {
        return new SupportQuestionReplyResponse(
                reply.id(),
                reply.supportQuestionId(),
                reply.authorUserId(),
                reply.body(),
                reply.createdAt()
        );
    }
}
