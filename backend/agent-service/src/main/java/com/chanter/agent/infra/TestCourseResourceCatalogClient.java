package com.chanter.agent.infra;

import com.chanter.agent.application.CourseResourceCatalogClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestCourseResourceCatalogClient implements CourseResourceCatalogClient {

    private final List<CourseResourceSummary> resources = new ArrayList<>();
    private final Map<UUID, Set<UUID>> allowedViewerIdsByCourse = new HashMap<>();

    public void registerResource(CourseResourceSummary resource) {
        resources.add(resource);
    }

    public void grantViewerAccess(UUID courseId, UUID viewerUserId) {
        allowedViewerIdsByCourse.computeIfAbsent(courseId, ignored -> new HashSet<>()).add(viewerUserId);
    }

    public void clear() {
        resources.clear();
        allowedViewerIdsByCourse.clear();
    }

    @Override
    public List<CourseResourceSummary> listAiApprovedCourseResources(UUID courseId, UUID viewerUserId) {
        Set<UUID> allowedViewerIds = allowedViewerIdsByCourse.get(courseId);
        if (allowedViewerIds != null && !allowedViewerIds.contains(viewerUserId)) {
            return List.of();
        }

        return resources.stream()
                .filter(resource -> resource.courseId().equals(courseId) && resource.aiApproved())
                .toList();
    }
}
