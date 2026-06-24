package com.chanter.agent.application;

import java.util.UUID;

public interface SupportQuestionChannelAccessClient {

    SupportQuestionChannelAccess requireAccess(UUID channelId, UUID userId);

    record SupportQuestionChannelAccess(
            UUID channelId,
            UUID courseId,
            UUID studyServerId,
            String channelName,
            boolean canPostSupportQuestion,
            boolean canViewUnansweredSupportQuestions
    ) {
    }
}
