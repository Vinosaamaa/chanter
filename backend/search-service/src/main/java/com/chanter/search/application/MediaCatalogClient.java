package com.chanter.search.application;

import java.util.List;
import java.util.UUID;

public interface MediaCatalogClient {

    List<CourseResourceSummary> listCourseResources(UUID courseId, UUID viewerUserId);

    record CourseResourceSummary(UUID id, UUID courseId, String title, String fileName) {
    }
}
