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
        Instant createdAt,
        String status,
        String sha256,
        String ingestionStatus,
        java.util.Set<String> ingestionSignals,
        UUID studyServerId,
        UUID cohortId
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
                courseResource.createdAt(),
                courseResource.publicStatus(),
                courseResource.sha256(), courseResource.ingestionStatus(), courseResource.ingestionSignals(),
                courseResource.studyServerId(), null
        );
    }
}
