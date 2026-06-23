package com.chanter.agent.application;

import java.util.List;
import java.util.UUID;

public interface CourseResourceCatalogClient {

    List<CourseResourceSummary> listAiApprovedCourseResources(UUID courseId, UUID viewerUserId);

    record CourseResourceSummary(
            UUID id,
            UUID courseId,
            String title,
            String fileName,
            boolean aiApproved
    ) {
    }
}
