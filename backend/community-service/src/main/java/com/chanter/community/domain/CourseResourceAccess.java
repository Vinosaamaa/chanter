package com.chanter.community.domain;

import java.util.UUID;

public record CourseResourceAccess(
        UUID courseId,
        boolean canUploadCourseResource,
        boolean canViewCourseResources
) {
}
