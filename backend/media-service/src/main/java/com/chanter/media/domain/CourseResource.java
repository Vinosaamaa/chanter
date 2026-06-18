package com.chanter.media.domain;

import java.time.Instant;
import java.util.UUID;

public record CourseResource(
        UUID id,
        UUID courseId,
        String title,
        String fileName,
        String contentType,
        long byteSize,
        String storageKey,
        boolean aiApproved,
        UUID uploadedByUserId,
        Instant createdAt
) {
}
