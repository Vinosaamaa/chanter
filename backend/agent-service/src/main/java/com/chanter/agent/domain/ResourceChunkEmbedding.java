package com.chanter.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record ResourceChunkEmbedding(
        UUID chunkId,
        UUID resourceId,
        UUID courseId,
        String modelId,
        int dimensions,
        float[] vector,
        Instant createdAt
) {
}
