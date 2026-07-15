package com.chanter.notification.application;

import com.chanter.notification.domain.Notification;
import com.chanter.notification.domain.NotificationFilterBucket;
import com.chanter.notification.domain.NotificationKind;
import com.chanter.notification.domain.NotificationListFilter;
import com.chanter.notification.domain.NotificationListStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification upsert(Notification notification);

    List<Notification> findForUser(
            UUID userId,
            NotificationListFilter filter,
            NotificationListStatus status,
            int limit
    );

    long countUnread(UUID userId);

    Optional<Notification> findByIdForUser(UUID notificationId, UUID userId);

    boolean markRead(UUID notificationId, UUID userId, Instant readAt);

    boolean markDone(UUID notificationId, UUID userId, Instant doneAt);

    record CreateCommand(
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
            UUID channelId
    ) {
    }
}
