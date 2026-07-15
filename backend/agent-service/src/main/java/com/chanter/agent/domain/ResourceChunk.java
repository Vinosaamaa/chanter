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
        Instant createdAt
) {
}
