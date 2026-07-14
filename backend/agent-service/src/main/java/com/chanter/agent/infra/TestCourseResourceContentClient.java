package com.chanter.agent.infra;

import com.chanter.agent.application.CourseResourceContentClient;
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
public class TestCourseResourceContentClient implements CourseResourceContentClient {

    private final Map<UUID, byte[]> contentByResourceId = new ConcurrentHashMap<>();
    private final Set<UUID> unavailableResourceIds = ConcurrentHashMap.newKeySet();

    public void registerContent(UUID resourceId, byte[] content) {
        contentByResourceId.put(resourceId, content);
    }

    public void registerUnavailable(UUID resourceId) {
        unavailableResourceIds.add(resourceId);
    }

    public void clear() {
        contentByResourceId.clear();
        unavailableResourceIds.clear();
    }

    @Override
    public byte[] downloadContent(UUID resourceId, UUID viewerUserId) {
        if (unavailableResourceIds.contains(resourceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Course Resource content is unavailable");
        }
        byte[] content = contentByResourceId.get(resourceId);
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource not found");
        }
        return content;
    }
}
