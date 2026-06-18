package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record DirectMessage(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        String body,
        Instant sentAt
) {
}
