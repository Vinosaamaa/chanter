package com.chanter.message.api;

import com.chanter.message.domain.DirectMessage;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageResponse(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        String body,
        Instant sentAt
) {

    public static DirectMessageResponse from(DirectMessage directMessage) {
        return new DirectMessageResponse(
                directMessage.id(),
                directMessage.senderUserId(),
                directMessage.recipientUserId(),
                directMessage.body(),
                directMessage.sentAt()
        );
    }
}
