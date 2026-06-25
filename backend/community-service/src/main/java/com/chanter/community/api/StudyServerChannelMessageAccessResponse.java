package com.chanter.community.api;

import com.chanter.community.domain.TextChannelMessageAccess;
import java.util.UUID;

public record StudyServerChannelMessageAccessResponse(
        UUID channelId,
        UUID studyServerId,
        String channelName,
        boolean canReadMessages,
        boolean canPostMessages
) {

    public static StudyServerChannelMessageAccessResponse from(TextChannelMessageAccess access) {
        return new StudyServerChannelMessageAccessResponse(
                access.channelId(),
                access.studyServerId(),
                access.channelName(),
                access.canReadMessages(),
                access.canPostMessages()
        );
    }
}
