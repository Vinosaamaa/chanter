package com.chanter.community.application;

import java.util.UUID;

public interface NotificationClient {

    void createNotification(
            UUID userId,
            String kind,
            String title,
            String bodyPreview,
            String courseLabel,
            String href,
            String sourceType,
            UUID sourceId,
            UUID studyServerId,
            UUID courseId,
            UUID cohortId,
            UUID channelId
    );
}
