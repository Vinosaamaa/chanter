package com.chanter.agent.application;

import java.time.Instant;
import java.util.UUID;

public interface SupportQuestionClient {

    SupportQuestion getSupportQuestion(UUID channelId, UUID supportQuestionId, UUID viewerUserId);

    SupportQuestion updateStatus(
            UUID channelId,
            UUID supportQuestionId,
            UUID actorUserId,
            String status
    );

    record SupportQuestion(
            UUID id,
            UUID channelId,
            UUID senderUserId,
            String body,
            String status,
            Instant createdAt
    ) {
    }
}
