package com.chanter.agent.infra;

import com.chanter.agent.application.ResourceChunkRepository;
import com.chanter.agent.application.ResourceChunkEmbeddingRepository;
import com.chanter.agent.application.ResourceIndexStore;
import com.chanter.agent.application.ResourceTextExtractor;
import com.chanter.agent.domain.ResourceChunk;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcResourceIndexStore implements ResourceIndexStore {
    private final JdbcClient jdbc;
    private final ResourceChunkRepository chunks;
    private final ResourceChunkEmbeddingRepository embeddings;
    private final com.chanter.agent.application.EmbeddingVersionStore versions;

    public JdbcResourceIndexStore(JdbcClient jdbc, ResourceChunkRepository chunks,
            ResourceChunkEmbeddingRepository embeddings, com.chanter.agent.application.EmbeddingVersionStore versions) {
        this.jdbc = jdbc; this.chunks = chunks; this.embeddings = embeddings;
        this.versions = versions;
    }

    @Override @Transactional
    public Attempt begin(UUID courseId, UUID resourceId, String fileName, String sourceSha256) {
        new com.chanter.agent.lifecycle.AgentLifecycleAccess(jdbc).requireScope(courseId,null);
        chunks.lockForIndexing(resourceId);
        var state = state(resourceId);
        if (state.sourceEventId() != null) throw conflict("Resource ingestion is managed by durable events");
        var existing = chunks.findByResourceId(resourceId);
        if ((state.courseId() != null && !state.courseId().equals(courseId))
                || existing.stream().anyMatch(c -> !c.courseId().equals(courseId))) {
            throw conflict("Resource index course cannot change");
        }
        if ("READY".equals(state.status()) && sourceSha256.equals(state.sourceSha256())
                && ResourceTextExtractor.PARSER_VERSION.equals(state.parserVersion())
                && fileName.equals(state.fileName())) {
            return new Attempt(state.generation(), true, existing, state.signals());
        }
        jdbc.sql("""
                UPDATE resource_index_lifecycle SET generation=generation+1, course_id=:course,
                    source_sha256=:sha, parser_version=:parser, file_name=:file, status='PROCESSING', signals=''
                WHERE resource_id=:id
                """).param("course", courseId).param("sha", sourceSha256)
                .param("parser", ResourceTextExtractor.PARSER_VERSION).param("file", fileName)
                .param("id", resourceId).update();
        // Old content is unavailable as soon as replacement starts; embeddings cascade with chunks.
        chunks.replaceAllForResource(resourceId, List.of());
        return new Attempt(state.generation() + 1, false, List.of(), Set.of());
    }

    @Override @Transactional
    public Attempt snapshot(UUID resourceId) {
        chunks.lockForIndexing(resourceId);
        var state = state(resourceId);
        if ("PROCESSING".equals(state.status())) throw conflict("Resource extraction is in progress");
        return new Attempt(state.generation(), false, chunks.findByResourceId(resourceId), state.signals());
    }

    @Override @Transactional
    public void complete(UUID resourceId, long generation, List<ResourceChunk> prepared,
            List<ResourceChunkEmbedding> vectors, String status, Set<String> signals) {
        requireCurrent(resourceId, generation);
        requireModelVersions(vectors, !prepared.isEmpty());
        chunks.replaceAllForResource(resourceId, prepared);
        embeddings.replaceAllForResource(resourceId, vectors);
        jdbc.sql("UPDATE resource_index_lifecycle SET status=:status, signals=:signals,job_lease_id=NULL,job_lease_until=NULL,job_retry_at=NULL WHERE resource_id=:id")
                .param("status", status).param("signals", signals.stream().sorted().collect(java.util.stream.Collectors.joining(",")))
                .param("id", resourceId).update();
    }

    @Override @Transactional
    public void fail(UUID resourceId, long generation) {
        // Do not overwrite a newer attempt or terminal deletion when an old preparation fails.
        jdbc.sql("""
                UPDATE resource_index_lifecycle SET status='FAILED'
                WHERE resource_id=:id AND generation=:generation AND deleted=FALSE AND status='PROCESSING'
                """).param("id", resourceId).param("generation", generation).update();
    }

    @Override @Transactional
    public void completeBackfill(UUID resourceId, Attempt snapshot, List<ResourceChunkEmbedding> prepared) {
        requireCurrent(resourceId, snapshot.generation());
        var currentIds = chunks.findByResourceId(resourceId).stream().map(ResourceChunk::id).toList();
        var snapshotIds = snapshot.chunks().stream().map(ResourceChunk::id).toList();
        if (!currentIds.equals(snapshotIds)) throw conflict("Resource chunks changed during embedding");
        requireModelVersions(prepared, false);
        embeddings.replaceModelsForResource(resourceId, prepared);
    }

    private void requireModelVersions(List<ResourceChunkEmbedding> prepared, boolean requireActive) {
        versions.lock();
        var modelIds=prepared.stream().map(ResourceChunkEmbedding::modelId).collect(java.util.stream.Collectors.toSet());
        if(!versions.writableIds().containsAll(modelIds) || requireActive && !modelIds.contains(versions.active().id())) {
            throw conflict("Embedding model changed during preparation");
        }
    }

    private void requireCurrent(UUID resourceId, long generation) {
        chunks.lockForIndexing(resourceId);
        if (state(resourceId).generation() != generation) throw conflict("Resource ingestion was superseded");
    }
    private State state(UUID resourceId) {
        return jdbc.sql("SELECT generation, course_id, source_sha256, parser_version, file_name, status, signals, source_event_id FROM resource_index_lifecycle WHERE resource_id=:id")
                .param("id", resourceId).query((rs, row) -> new State(rs.getLong("generation"),
                        rs.getObject("course_id", UUID.class), rs.getString("source_sha256"),
                        rs.getString("parser_version"), rs.getString("file_name"), rs.getString("status"), rs.getString("signals").isBlank() ? Set.of() : Set.of(rs.getString("signals").split(",")), rs.getObject("source_event_id", UUID.class))).single();
    }
    private record State(long generation, UUID courseId, String sourceSha256, String parserVersion, String fileName, String status, Set<String> signals, UUID sourceEventId) {}
    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
