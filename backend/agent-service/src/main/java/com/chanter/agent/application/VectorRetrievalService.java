package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunk;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VectorRetrievalService {

    private final ResourceChunkRepository chunkRepository;
    private final ResourceChunkEmbeddingRepository embeddingRepository;
    private final EmbeddingClient embeddingClient;

    public VectorRetrievalService(
            ResourceChunkRepository chunkRepository,
            ResourceChunkEmbeddingRepository embeddingRepository,
            EmbeddingClient embeddingClient
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingClient = embeddingClient;
    }

    @Transactional(readOnly = true)
    public List<RankedChunk> retrieve(String query, Set<UUID> grantedResourceIds, int topK) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        if (grantedResourceIds == null || grantedResourceIds.isEmpty()) {
            return List.of();
        }
        int limit = topK < 1 ? 5 : Math.min(topK, 50);

        float[] queryVector = embeddingClient.embed(query);
        List<ResourceChunkEmbedding> embeddings = embeddingRepository.findByResourceIds(grantedResourceIds);
        if (embeddings.isEmpty()) {
            return List.of();
        }

        Set<UUID> chunkIds = new HashSet<>();
        for (ResourceChunkEmbedding embedding : embeddings) {
            chunkIds.add(embedding.chunkId());
        }

        Map<UUID, ResourceChunk> chunksById = new HashMap<>();
        for (UUID resourceId : grantedResourceIds) {
            for (ResourceChunk chunk : chunkRepository.findByResourceId(resourceId)) {
                if (chunkIds.contains(chunk.id())) {
                    chunksById.put(chunk.id(), chunk);
                }
            }
        }

        List<RankedChunk> ranked = new ArrayList<>();
        for (ResourceChunkEmbedding embedding : embeddings) {
            ResourceChunk chunk = chunksById.get(embedding.chunkId());
            if (chunk == null) {
                continue;
            }
            double score = EmbeddingCodec.cosineSimilarity(queryVector, embedding.vector());
            ranked.add(new RankedChunk(
                    chunk.id(),
                    chunk.resourceId(),
                    chunk.courseId(),
                    chunk.chunkIndex(),
                    chunk.startOffset(),
                    chunk.endOffset(),
                    chunk.contentText(),
                    chunk.fileName(),
                    score,
                    embedding.modelId()
            ));
        }

        ranked.sort(Comparator.comparingDouble(RankedChunk::score).reversed());
        if (ranked.size() > limit) {
            return List.copyOf(ranked.subList(0, limit));
        }
        return List.copyOf(ranked);
    }

    public record RankedChunk(
            UUID chunkId,
            UUID resourceId,
            UUID courseId,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String contentText,
            String fileName,
            double score,
            String modelId
    ) {
    }
}
