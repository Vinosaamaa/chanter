package com.chanter.agent.infra;

import com.chanter.agent.application.CourseResourceContentClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestCourseResourceContentClient implements CourseResourceContentClient {

    private final Map<UUID, byte[]> contentByResourceId = new ConcurrentHashMap<>();

    public void registerContent(UUID resourceId, byte[] content) {
        contentByResourceId.put(resourceId, content);
    }

    public void clear() {
        contentByResourceId.clear();
    }

    @Override
    public byte[] downloadContent(UUID resourceId, UUID viewerUserId) {
        byte[] content = contentByResourceId.get(resourceId);
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource not found");
        }
        return content;
    }
}
