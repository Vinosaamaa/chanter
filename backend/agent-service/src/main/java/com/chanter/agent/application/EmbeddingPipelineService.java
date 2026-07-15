package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunk;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmbeddingPipelineService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingPipelineService.class);

    private final ResourceChunkRepository chunkRepository;
    private final ResourceChunkEmbeddingRepository embeddingRepository;
    private final EmbeddingClient embeddingClient;
    private final Clock clock;

    public EmbeddingPipelineService(
            ResourceChunkRepository chunkRepository,
            ResourceChunkEmbeddingRepository embeddingRepository,
            EmbeddingClient embeddingClient,
            Clock clock
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingClient = embeddingClient;
        this.clock = clock;
    }

    @Transactional
    public EmbedResult embedResource(UUID resourceId) {
        if (resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId is required");
        }
        List<ResourceChunk> chunks = chunkRepository.findByResourceId(resourceId);
        if (chunks.isEmpty()) {
            embeddingRepository.deleteByResourceId(resourceId);
            log.info("Embedding pipeline cleared embeddings resourceId={} reason=no_chunks", resourceId);
            return new EmbedResult(resourceId, 0, embeddingClient.modelId());
        }

        var createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<ResourceChunkEmbedding> embeddings = new ArrayList<>(chunks.size());
        for (ResourceChunk chunk : chunks) {
            float[] vector = embeddingClient.embed(chunk.contentText());
            embeddings.add(new ResourceChunkEmbedding(
                    chunk.id(),
                    chunk.resourceId(),
                    chunk.courseId(),
                    embeddingClient.modelId(),
                    embeddingClient.dimensions(),
                    vector,
                    createdAt
            ));
        }
        embeddingRepository.replaceAllForResource(resourceId, embeddings);
        log.info(
                "Embedding pipeline stored embeddings resourceId={} chunkCount={} modelId={}",
                resourceId,
                embeddings.size(),
                embeddingClient.modelId()
        );
        return new EmbedResult(resourceId, embeddings.size(), embeddingClient.modelId());
    }

    @Transactional
    public EmbedResult backfillResource(UUID resourceId) {
        return embedResource(resourceId);
    }

    public record EmbedResult(UUID resourceId, int embeddingCount, String modelId) {
    }
}
