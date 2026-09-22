package com.chanter.community.domain;

import java.util.UUID;

public record CourseChannelMessageAccess(
        UUID channelId,
        UUID courseId,
        UUID studyServerId,
        String channelName,
        boolean canReadMessages,
        boolean canPostMessages
) {
}
