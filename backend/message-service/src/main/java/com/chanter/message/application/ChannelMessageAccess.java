package com.chanter.message.application;

import com.chanter.message.domain.ChannelScope;
import java.util.UUID;

public record ChannelMessageAccess(
        UUID channelId,
        ChannelScope channelScope,
        boolean canReadMessages,
        boolean canPostMessages,
        UUID studyServerId,
        UUID courseId
) {
    public ChannelMessageAccess(UUID channelId, ChannelScope scope, boolean canRead, boolean canPost) {
        this(channelId, scope, canRead, canPost, null, null);
    }
}
