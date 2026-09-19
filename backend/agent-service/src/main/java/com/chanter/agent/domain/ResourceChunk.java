package com.chanter.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record ResourceChunk(
        UUID id,
        UUID resourceId,
        UUID courseId,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String contentText,
        String contentSha256,
        String fileName,
        Instant createdAt,
        String locatorKind,
        Integer locatorNumber,
        String locatorLabel,
        String sourceSha256,
        String parserVersion
) {
    public ResourceChunk(UUID id, UUID resourceId, UUID courseId, int chunkIndex, int startOffset,
            int endOffset, String contentText, String contentSha256, String fileName, Instant createdAt) {
        this(id, resourceId, courseId, chunkIndex, startOffset, endOffset, contentText, contentSha256,
                fileName, createdAt, null, null, null, null, null);
    }
}
