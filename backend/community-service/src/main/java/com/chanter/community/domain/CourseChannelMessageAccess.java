package com.chanter.community.domain;

import java.util.UUID;

public record CourseChannelMessageAccess(
        UUID channelId,
        UUID courseId,
        String channelName,
        boolean canReadMessages,
        boolean canPostMessages
) {
}
