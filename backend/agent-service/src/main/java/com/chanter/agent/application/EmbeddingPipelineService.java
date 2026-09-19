package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunk;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmbeddingPipelineService {

    private final ResourceIndexStore indexStore;
    private final EmbeddingClient embeddingClient;
    private final Clock clock;
    private final EmbeddingVersionStore versions;

    public EmbeddingPipelineService(
            ResourceIndexStore indexStore,
            EmbeddingClient embeddingClient,
            Clock clock, EmbeddingVersionStore versions
    ) {
        this.indexStore = indexStore;
        this.embeddingClient = embeddingClient;
        this.clock = clock;
        this.versions = versions;
    }

    public EmbedResult embedResource(UUID resourceId) {
        if (resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId is required");
        }
        var snapshot = indexStore.snapshot(resourceId);
        var prepared = prepare(snapshot.chunks());
        indexStore.completeBackfill(resourceId, snapshot, prepared);
        return new EmbedResult(resourceId, prepared.size(), embeddingClient.modelId());
    }

    public List<ResourceChunkEmbedding> prepare(List<ResourceChunk> chunks) {
        versions.initializeDefault(embeddingClient.metadata());
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
        return List.copyOf(embeddings);
    }

    public EmbedResult backfillResource(UUID resourceId) {
        return embedResource(resourceId);
    }

    public record EmbedResult(UUID resourceId, int embeddingCount, String modelId) {
    }
}
