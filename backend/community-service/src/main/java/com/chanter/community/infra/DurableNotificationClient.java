package com.chanter.community.infra;

import com.chanter.community.application.NotificationClient;
import com.chanter.common.events.NotificationEventWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DurableNotificationClient implements NotificationClient {
    private final NotificationEventWriter writer;
    public DurableNotificationClient(NotificationEventWriter writer) { this.writer = writer; }

    @Override
    public void createNotification(
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
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("kind", kind);
        body.put("title", title);
        if (bodyPreview != null) {
            body.put("bodyPreview", bodyPreview);
        }
        if (courseLabel != null) {
            body.put("courseLabel", courseLabel);
        }
        body.put("href", href);
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        if (studyServerId != null) {
            body.put("studyServerId", studyServerId);
        }
        if (courseId != null) {
            body.put("courseId", courseId);
        }
        if (cohortId != null) {
            body.put("cohortId", cohortId);
        }
        if (channelId != null) {
            body.put("channelId", channelId);
        }

        writer.append(body);
    }
}
