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
    private final CourseResourceCatalogClient resources;

    public VectorRetrievalService(
            ResourceChunkRepository chunkRepository,
            ResourceChunkEmbeddingRepository embeddingRepository,
            EmbeddingClient embeddingClient,
            CourseResourceCatalogClient resources
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingClient = embeddingClient;
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public List<RankedChunk> retrieve(String query, UUID courseId, UUID viewerUserId, Set<UUID> grantedResourceIds, int topK) {
        if (courseId == null || viewerUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course and viewer are required for resource retrieval");
        }
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        if (grantedResourceIds == null || grantedResourceIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> authorized = new HashSet<>();
        for (var resource : resources.listAiApprovedCourseResources(courseId, viewerUserId)) {
            if (resource.aiApproved() && courseId.equals(resource.courseId()) && grantedResourceIds.contains(resource.id())) authorized.add(resource.id());
        }
        if (authorized.isEmpty()) return List.of();
        int limit = topK < 1 ? 5 : Math.min(topK, 50);

        float[] queryVector = embeddingClient.embed(query);
        List<ResourceChunkEmbedding> embeddings = embeddingRepository.findByResourceIds(authorized);
        if (embeddings.isEmpty()) {
            return List.of();
        }

        Set<UUID> chunkIds = new HashSet<>();
        for (ResourceChunkEmbedding embedding : embeddings) {
            chunkIds.add(embedding.chunkId());
        }

        Map<UUID, ResourceChunk> chunksById = new HashMap<>();
        for (UUID resourceId : authorized) {
            for (ResourceChunk chunk : chunkRepository.findByResourceId(resourceId)) {
                if (chunkIds.contains(chunk.id()) && courseId.equals(chunk.courseId()) && resourceId.equals(chunk.resourceId())) {
                    chunksById.put(chunk.id(), chunk);
                }
            }
        }

        List<RankedChunk> ranked = new ArrayList<>();
        for (ResourceChunkEmbedding embedding : embeddings) {
            ResourceChunk chunk = chunksById.get(embedding.chunkId());
            if (chunk == null || !courseId.equals(embedding.courseId()) || !chunk.resourceId().equals(embedding.resourceId())) {
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
                    embedding.modelId(), chunk.locatorKind(), chunk.locatorNumber(), chunk.locatorLabel(),
                    chunk.sourceSha256(), chunk.parserVersion(), chunk.extractionSignals()
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
            String modelId,
            String locatorKind,
            Integer locatorNumber,
            String locatorLabel,
            String sourceSha256,
            String parserVersion,
            Set<String> extractionSignals
    ) {
        public RankedChunk { extractionSignals = Set.copyOf(extractionSignals); }
        public RankedChunk(UUID chunkId, UUID resourceId, UUID courseId, int chunkIndex, int startOffset,
                int endOffset, String contentText, String fileName, double score, String modelId,
                String locatorKind, Integer locatorNumber, String locatorLabel, String sourceSha256, String parserVersion) {
            this(chunkId, resourceId, courseId, chunkIndex, startOffset, endOffset, contentText, fileName,
                    score, modelId, locatorKind, locatorNumber, locatorLabel, sourceSha256, parserVersion, Set.of());
        }
        public RankedChunk(UUID chunkId, UUID resourceId, UUID courseId, int chunkIndex, int startOffset,
                int endOffset, String contentText, String fileName, double score, String modelId) {
            this(chunkId, resourceId, courseId, chunkIndex, startOffset, endOffset, contentText, fileName,
                    score, modelId, null, null, null, null, null);
        }
    }
}
