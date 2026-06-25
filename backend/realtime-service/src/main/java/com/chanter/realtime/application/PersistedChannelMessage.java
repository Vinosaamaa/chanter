package com.chanter.realtime.application;

import com.chanter.realtime.domain.RealtimeChannelScope;
import java.time.Instant;
import java.util.UUID;

public record PersistedChannelMessage(
        UUID id,
        UUID channelId,
        UUID senderUserId,
        String body,
        Instant createdAt
) {
}
