package com.chanter.community.infra;

import com.chanter.community.application.NotificationClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestNotificationClient implements NotificationClient {

    public record CreateCall(
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
    }

    private final List<CreateCall> calls = new ArrayList<>();

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
        calls.add(new CreateCall(
                userId,
                kind,
                title,
                bodyPreview,
                courseLabel,
                href,
                sourceType,
                sourceId,
                studyServerId,
                courseId,
                cohortId,
                channelId
        ));
    }

    public List<CreateCall> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
    }
}
