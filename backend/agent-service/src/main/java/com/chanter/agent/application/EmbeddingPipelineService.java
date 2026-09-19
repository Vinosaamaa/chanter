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
    private final EmbeddingModelRouter embeddingClient;
    private final Clock clock;

    public EmbeddingPipelineService(
            ResourceIndexStore indexStore,
            EmbeddingModelRouter embeddingClient,
            Clock clock
    ) {
        this.indexStore = indexStore;
        this.embeddingClient = embeddingClient;
        this.clock = clock;
    }

    public EmbedResult embedResource(UUID resourceId) {
        if (resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId is required");
        }
        var snapshot = indexStore.snapshot(resourceId);
        var selected = embeddingClient.pinned();
        var prepared = prepareWith(selected, snapshot.chunks());
        indexStore.completeBackfill(resourceId, snapshot, prepared);
        return new EmbedResult(resourceId, prepared.size(), selected.modelId());
    }

    public List<ResourceChunkEmbedding> prepare(List<ResourceChunk> chunks) {
        return prepareWith(embeddingClient.pinned(),chunks);
    }
    public List<ResourceChunkEmbedding> prepareFor(String modelId,List<ResourceChunk> chunks) {
        return prepareWith(embeddingClient.client(modelId),chunks);
    }
    private List<ResourceChunkEmbedding> prepareWith(EmbeddingClient selected,List<ResourceChunk> chunks) {
        var createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<ResourceChunkEmbedding> embeddings = new ArrayList<>(chunks.size());
        for (ResourceChunk chunk : chunks) {
            float[] vector = selected.embed(chunk.contentText());
            embeddings.add(new ResourceChunkEmbedding(
                    chunk.id(),
                    chunk.resourceId(),
                    chunk.courseId(),
                    selected.modelId(),
                    selected.dimensions(),
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
