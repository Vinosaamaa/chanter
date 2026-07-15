package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceChunkRepository {

    void replaceAllForResource(UUID resourceId, List<ResourceChunk> chunks);

    void deleteByResourceId(UUID resourceId);

    Optional<ResourceChunk> findById(UUID chunkId);

    List<ResourceChunk> findByResourceId(UUID resourceId);

    List<ResourceChunk> findByCourseId(UUID courseId);

    int countByResourceId(UUID resourceId);
}
