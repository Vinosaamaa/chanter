package com.chanter.agent.application;

import java.util.List;
import java.util.UUID;

public interface CourseResourceCatalogClient {

    /** Current AVAILABLE, AI-approved resources in this course that media authorizes the viewer to read. */
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
