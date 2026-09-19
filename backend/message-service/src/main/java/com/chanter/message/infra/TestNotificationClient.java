package com.chanter.message.infra;

import com.chanter.message.application.NotificationClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestNotificationClient implements NotificationClient {
    private final DurableNotificationClient delegate;
    public TestNotificationClient(com.chanter.common.events.NotificationEventWriter writer) {
        this.delegate = new DurableNotificationClient(writer);
    }


    public record SupportQuestionAnsweredCall(
            UUID recipientUserId,
            UUID supportQuestionId,
            UUID channelId,
            UUID courseId,
            String title,
            String bodyPreview,
            String courseLabel
    ) {
    }

    private final List<SupportQuestionAnsweredCall> calls = new ArrayList<>();

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
        delegate.notifySupportQuestionAnswered(recipientUserId, supportQuestionId, channelId, courseId, title, bodyPreview, courseLabel);
        calls.add(new SupportQuestionAnsweredCall(
                recipientUserId,
                supportQuestionId,
                channelId,
                courseId,
                title,
                bodyPreview,
                courseLabel
        ));
    }

    public List<SupportQuestionAnsweredCall> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
    }
}
