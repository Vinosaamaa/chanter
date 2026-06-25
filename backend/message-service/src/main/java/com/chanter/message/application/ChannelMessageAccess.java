package com.chanter.message.application;

import com.chanter.message.domain.ChannelScope;
import java.util.UUID;

public record ChannelMessageAccess(
        UUID channelId,
        ChannelScope channelScope,
        boolean canReadMessages,
        boolean canPostMessages
) {
}
