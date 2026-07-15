package com.chanter.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID userId,
        NotificationKind kind,
        NotificationFilterBucket filterBucket,
        String title,
        String bodyPreview,
        String courseLabel,
        String href,
        String sourceType,
        UUID sourceId,
        UUID studyServerId,
        UUID courseId,
        UUID cohortId,
        UUID channelId,
        Instant createdAt,
        Instant readAt,
        Instant doneAt
) {

    public boolean unread() {
        return readAt == null && doneAt == null;
    }

    public boolean open() {
        return doneAt == null;
    }
}
