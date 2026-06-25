package com.chanter.community.domain;

import java.util.UUID;

public record TextChannelMessageAccess(
        UUID channelId,
        UUID studyServerId,
        String channelName,
        boolean canReadMessages,
        boolean canPostMessages
) {
}
