package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record ChannelMessage(
        UUID id,
        UUID channelId,
        UUID senderUserId,
        String body,
        Instant createdAt
) {
}
