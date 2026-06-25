package com.chanter.community.api;

import com.chanter.community.domain.CourseChannelMessageAccess;
import java.util.UUID;

public record CourseChannelMessageAccessResponse(
        UUID channelId,
        UUID courseId,
        String channelName,
        boolean canReadMessages,
        boolean canPostMessages
) {

    public static CourseChannelMessageAccessResponse from(CourseChannelMessageAccess access) {
        return new CourseChannelMessageAccessResponse(
                access.channelId(),
                access.courseId(),
                access.channelName(),
                access.canReadMessages(),
                access.canPostMessages()
        );
    }
}
