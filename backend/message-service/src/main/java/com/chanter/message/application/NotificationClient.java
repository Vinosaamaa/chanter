package com.chanter.message.application;

import java.util.UUID;

public interface NotificationClient {
    void notifySavedAnswer(UUID answerId,UUID recipientUserId,UUID supportQuestionId,UUID channelId,UUID courseId,
            String title,String bodyPreview,String courseLabel);

    void notifySupportQuestionAnswered(
            UUID recipientUserId,
            UUID supportQuestionId,
            UUID channelId,
            UUID courseId,
            String title,
            String bodyPreview,
            String courseLabel
    );
}
