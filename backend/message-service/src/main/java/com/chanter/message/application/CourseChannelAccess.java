package com.chanter.message.application;

import java.util.UUID;

public record CourseChannelAccess(
        UUID channelId,
        UUID courseId,
        String channelName,
        boolean canPostSupportQuestion,
        boolean canViewUnansweredSupportQuestions
) {
}
