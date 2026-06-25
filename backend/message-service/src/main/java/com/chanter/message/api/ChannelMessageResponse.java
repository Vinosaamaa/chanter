package com.chanter.message.api;

import com.chanter.message.domain.ChannelMessage;
import java.time.Instant;
import java.util.UUID;

public record ChannelMessageResponse(
        UUID id,
        UUID channelId,
        UUID senderUserId,
        String body,
        Instant createdAt
) {

    public static ChannelMessageResponse from(ChannelMessage message) {
        return new ChannelMessageResponse(
                message.id(),
                message.channelId(),
                message.senderUserId(),
                message.body(),
                message.createdAt()
        );
    }
}
