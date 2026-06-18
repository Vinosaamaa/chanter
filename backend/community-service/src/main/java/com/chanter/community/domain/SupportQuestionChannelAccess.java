package com.chanter.community.domain;

import java.util.UUID;

public record SupportQuestionChannelAccess(
        UUID channelId,
        UUID courseId,
        String channelName,
        boolean canPostSupportQuestion,
        boolean canViewUnansweredSupportQuestions
) {
}
