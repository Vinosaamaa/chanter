package com.chanter.message.application;

import java.util.UUID;

public record CourseResourceAccess(
        UUID courseId,
        boolean canUploadCourseResource,
        boolean canViewCourseResources
) {
}
