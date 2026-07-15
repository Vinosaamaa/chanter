package com.chanter.notification.api;

import com.chanter.notification.domain.NotificationFilterBucket;
import com.chanter.notification.domain.NotificationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateNotificationRequest(
        @NotNull UUID userId,
        @NotNull NotificationKind kind,
        NotificationFilterBucket filterBucket,
        @NotBlank @Size(max = 512) String title,
        @Size(max = 2000) String bodyPreview,
        @Size(max = 255) String courseLabel,
        @NotBlank @Size(max = 1024) String href,
        @NotBlank @Size(max = 64) String sourceType,
        @NotNull UUID sourceId,
        UUID studyServerId,
        UUID courseId,
        UUID cohortId,
        UUID channelId
) {
}
