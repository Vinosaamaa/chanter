package com.chanter.agent.application;

import com.chanter.agent.infra.JdbcVectorSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VectorRetrievalService {
    private final JdbcVectorSearch search;
    private final EmbeddingModelRouter models;
    private final CourseResourceCatalogClient resources;
    private final double minimumScore;

    public VectorRetrievalService(JdbcVectorSearch search, EmbeddingModelRouter models,
            CourseResourceCatalogClient resources,
            @Value("${chanter.grounding.min-retrieval-score:0.35}") double minimumScore) {
        if(!Double.isFinite(minimumScore) || minimumScore<0 || minimumScore>1)
            throw new IllegalArgumentException("Semantic similarity threshold must be between zero and one");
        this.search=search; this.models=models; this.resources=resources; this.minimumScore=minimumScore;
    }

    public List<RankedChunk> retrieve(String query, UUID courseId, UUID viewerUserId, Set<UUID> grantedResourceIds, int topK) {
        if(courseId==null || viewerUserId==null || query==null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Course, viewer and query are required");
        }
        if(grantedResourceIds==null || grantedResourceIds.isEmpty()) return List.of();
        if(query.codePoints().noneMatch(Character::isLetterOrDigit)) return List.of();
        var authorized=new ArrayList<JdbcVectorSearch.AuthorizedResource>();
        for(var resource:resources.listAiApprovedCourseResources(courseId,viewerUserId)) {
            if(!resource.aiApproved() || !courseId.equals(resource.courseId()) || !grantedResourceIds.contains(resource.id())) continue;
            // Unknown/missing source metadata never expands authorization.
            if(resource.studyServerId()==null || resource.sourceSha256()==null || !resource.sourceSha256().matches("[a-f0-9]{64}")) continue;
            authorized.add(new JdbcVectorSearch.AuthorizedResource(resource.id(),resource.studyServerId(),resource.cohortId(),resource.sourceSha256()));
        }
        if(authorized.isEmpty()) return List.of();
        try {
            var client=models.pinned();
            var model=client.metadata();
            float[] vector;
            try { vector=client.embed(query); }
            catch(java.util.concurrent.CancellationException cancelled) { throw cancelled; }
            catch(IllegalStateException unavailable) { throw new SemanticRetrievalUnavailableException(); }
            if(java.util.stream.IntStream.range(0,vector.length).allMatch(i->vector[i]==0)) return List.of();
            return search.nearest(model,courseId,authorized,vector,topK<1?5:Math.min(topK,50),minimumScore);
        } catch(org.springframework.dao.DataAccessException unavailable) { throw new SemanticRetrievalUnavailableException(); }
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
            Set<String> extractionSignals,
            com.chanter.agent.domain.ResourceSourceScope sourceScope
    ) {
        public RankedChunk { extractionSignals = Set.copyOf(extractionSignals); }
        public RankedChunk(UUID chunkId, UUID resourceId, UUID courseId, int chunkIndex, int startOffset,
                int endOffset, String contentText, String fileName, double score, String modelId,
                String locatorKind, Integer locatorNumber, String locatorLabel, String sourceSha256, String parserVersion,
                Set<String> extractionSignals) {
            this(chunkId, resourceId, courseId, chunkIndex, startOffset, endOffset, contentText, fileName,
                    score, modelId, locatorKind, locatorNumber, locatorLabel, sourceSha256, parserVersion, extractionSignals,
                    com.chanter.agent.domain.ResourceSourceScope.course(null, 0));
        }
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
