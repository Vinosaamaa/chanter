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
        Instant createdAt,
        String state,
        String sha256,
        UUID idempotencyKey,
        String storageBackend
) {
    public String publicStatus() {
        return switch (state) {
            case "AVAILABLE" -> "AVAILABLE";
            case "REJECTED" -> "REJECTED";
            case "SCAN_FAILED", "DELETE_PENDING", "DELETED" -> "FAILED";
            default -> "PROCESSING";
        };
    }
}
