package com.chanter.message.infra;

import com.chanter.message.application.NotificationClient;
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
    public void notifySupportQuestionAnswered(
            UUID recipientUserId,
            UUID supportQuestionId,
            UUID channelId,
            UUID courseId,
            String title,
            String bodyPreview,
            String courseLabel
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", recipientUserId);
        body.put("kind", "SUPPORT_QUESTION_ANSWERED");
        body.put("title", title);
        body.put("bodyPreview", bodyPreview);
        body.put("courseLabel", courseLabel);
        body.put("href", "/app/inbox?channelId=" + channelId + "&questionId=" + supportQuestionId);
        body.put("sourceType", "SUPPORT_QUESTION");
        body.put("sourceId", supportQuestionId);
        body.put("courseId", courseId);
        body.put("channelId", channelId);

        writer.append(body);
    }
}
