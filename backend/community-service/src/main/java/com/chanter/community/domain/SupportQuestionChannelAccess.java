package com.chanter.community.domain;

import java.util.UUID;

public record SupportQuestionChannelAccess(
        UUID channelId,
        UUID courseId,
        UUID studyServerId,
        String channelName,
        boolean canPostSupportQuestion,
        boolean canViewUnansweredSupportQuestions
) {
}
