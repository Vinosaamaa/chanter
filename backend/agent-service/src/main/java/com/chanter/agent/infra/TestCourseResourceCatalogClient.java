package com.chanter.agent.infra;

import com.chanter.agent.application.CourseResourceCatalogClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestCourseResourceCatalogClient implements CourseResourceCatalogClient {

    private final List<CourseResourceSummary> resources = new ArrayList<>();

    public void registerResource(CourseResourceSummary resource) {
        resources.add(resource);
    }

    public void clear() {
        resources.clear();
    }

    @Override
    public List<CourseResourceSummary> listAiApprovedCourseResources(UUID courseId, UUID viewerUserId) {
        return resources.stream()
                .filter(resource -> resource.courseId().equals(courseId) && resource.aiApproved())
                .toList();
    }
}
