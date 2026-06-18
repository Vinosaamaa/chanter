package com.chanter.message.infra;

import com.chanter.message.application.CourseChannelAccess;
import com.chanter.message.application.CourseChannelAccessClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestCourseChannelAccessClient implements CourseChannelAccessClient {

    private final Map<String, CourseChannelAccess> accessRules = new ConcurrentHashMap<>();
    private final Set<UUID> existingChannels = ConcurrentHashMap.newKeySet();

    public void registerChannel(UUID channelId) {
        existingChannels.add(channelId);
    }

    public void grantLearnerPost(UUID channelId, UUID userId, UUID courseId, String channelName) {
        registerChannel(channelId);
        accessRules.put(key(channelId, userId), new CourseChannelAccess(
                channelId,
                courseId,
                channelName,
                true,
                false
        ));
    }

    public void grantInstructorView(UUID channelId, UUID userId, UUID courseId, String channelName) {
        registerChannel(channelId);
        accessRules.put(key(channelId, userId), new CourseChannelAccess(
                channelId,
                courseId,
                channelName,
                false,
                true
        ));
    }

    public void clear() {
        accessRules.clear();
        existingChannels.clear();
    }

    @Override
    public CourseChannelAccess requireAccess(UUID channelId, UUID userId) {
        CourseChannelAccess access = accessRules.get(key(channelId, userId));
        if (access != null) {
            return access;
        }
        if (!existingChannels.contains(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course Channel access requires Cohort Enrollment or Instructor role");
    }

    private static String key(UUID channelId, UUID userId) {
        return channelId + ":" + userId;
    }
}
