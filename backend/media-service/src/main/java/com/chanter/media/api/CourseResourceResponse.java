package com.chanter.media.api;

import com.chanter.media.domain.CourseResource;
import java.time.Instant;
import java.util.UUID;

public record CourseResourceResponse(
        UUID id,
        UUID courseId,
        String title,
        String fileName,
        String contentType,
        long byteSize,
        boolean aiApproved,
        UUID uploadedByUserId,
        Instant createdAt
) {

    public static CourseResourceResponse from(CourseResource courseResource) {
        return new CourseResourceResponse(
                courseResource.id(),
                courseResource.courseId(),
                courseResource.title(),
                courseResource.fileName(),
                courseResource.contentType(),
                courseResource.byteSize(),
                courseResource.aiApproved(),
                courseResource.uploadedByUserId(),
                courseResource.createdAt()
        );
    }
}
