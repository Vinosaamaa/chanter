package com.chanter.community.api;

import com.chanter.community.domain.CourseResourceAccess;
import java.util.UUID;

public record CourseResourceAccessResponse(
        UUID courseId,
        boolean canUploadCourseResource,
        boolean canViewCourseResources
) {

    public static CourseResourceAccessResponse from(CourseResourceAccess access) {
        return new CourseResourceAccessResponse(
                access.courseId(),
                access.canUploadCourseResource(),
                access.canViewCourseResources()
        );
    }
}
