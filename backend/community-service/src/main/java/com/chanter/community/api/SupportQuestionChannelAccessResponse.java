package com.chanter.community.api;

import com.chanter.community.domain.SupportQuestionChannelAccess;
import java.util.UUID;

public record SupportQuestionChannelAccessResponse(
        UUID channelId,
        UUID courseId,
        UUID studyServerId,
        String channelName,
        boolean canPostSupportQuestion,
        boolean canViewUnansweredSupportQuestions
) {

    public static SupportQuestionChannelAccessResponse from(SupportQuestionChannelAccess access) {
        return new SupportQuestionChannelAccessResponse(
                access.channelId(),
                access.courseId(),
                access.studyServerId(),
                access.channelName(),
                access.canPostSupportQuestion(),
                access.canViewUnansweredSupportQuestions()
        );
    }
}
