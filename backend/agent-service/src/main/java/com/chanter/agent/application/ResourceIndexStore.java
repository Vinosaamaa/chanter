package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunk;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Short atomic boundaries around preparation; no parser or embedding client runs under these locks. */
public interface ResourceIndexStore {
    record Attempt(long generation, boolean alreadyReady, List<ResourceChunk> chunks, Set<String> signals) {}
    Attempt begin(UUID courseId, UUID resourceId, String fileName, String sourceSha256);
    Attempt snapshot(UUID resourceId);
    void complete(UUID resourceId, long generation, List<ResourceChunk> chunks,
            List<ResourceChunkEmbedding> embeddings, String status, Set<String> signals);
    void fail(UUID resourceId, long generation);
    void completeBackfill(UUID resourceId, Attempt snapshot, List<ResourceChunkEmbedding> embeddings);
}
