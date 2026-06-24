package com.chanter.agent.infra;

import com.chanter.agent.application.SupportQuestionChannelAccessClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestSupportQuestionChannelAccessClient implements SupportQuestionChannelAccessClient {

    private final Map<String, SupportQuestionChannelAccess> accessRules = new ConcurrentHashMap<>();

    public void grantLearnerPost(
            UUID channelId,
            UUID userId,
            UUID courseId,
            UUID studyServerId,
            String channelName
    ) {
        accessRules.put(key(channelId, userId), new SupportQuestionChannelAccess(
                channelId,
                courseId,
                studyServerId,
                channelName,
                true,
                false
        ));
    }

    public void clear() {
        accessRules.clear();
    }

    @Override
    public SupportQuestionChannelAccess requireAccess(UUID channelId, UUID userId) {
        SupportQuestionChannelAccess access = accessRules.get(key(channelId, userId));
        if (access == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course Channel access requires Cohort Enrollment or Instructor role");
        }
        return access;
    }

    private static String key(UUID channelId, UUID userId) {
        return channelId + ":" + userId;
    }
}
