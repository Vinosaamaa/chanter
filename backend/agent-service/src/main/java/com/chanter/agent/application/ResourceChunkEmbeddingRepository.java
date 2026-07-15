package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceChunkEmbeddingRepository {

    void replaceAllForResource(UUID resourceId, List<ResourceChunkEmbedding> embeddings);

    void deleteByResourceId(UUID resourceId);

    List<ResourceChunkEmbedding> findByResourceIds(Collection<UUID> resourceIds);
}
