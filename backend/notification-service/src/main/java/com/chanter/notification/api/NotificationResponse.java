package com.chanter.notification.api;

import com.chanter.notification.domain.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String kind,
        String filterBucket,
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
        Instant doneAt,
        boolean unread
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.userId(),
                notification.kind().name(),
                notification.filterBucket().name(),
                notification.title(),
                notification.bodyPreview(),
                notification.courseLabel(),
                notification.href(),
                notification.sourceType(),
                notification.sourceId(),
                notification.studyServerId(),
                notification.courseId(),
                notification.cohortId(),
                notification.channelId(),
                notification.createdAt(),
                notification.readAt(),
                notification.doneAt(),
                notification.unread()
        );
    }
}
