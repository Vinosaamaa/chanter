package com.chanter.realtime.application;

import java.time.Instant;
import java.util.UUID;

public record PersistedDirectMessage(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        String body,
        Instant sentAt
) {
}
