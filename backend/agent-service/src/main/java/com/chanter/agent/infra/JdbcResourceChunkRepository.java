package com.chanter.agent.infra;

import com.chanter.agent.application.ResourceChunkRepository;
import com.chanter.agent.domain.ResourceChunk;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcResourceChunkRepository implements ResourceChunkRepository {

    private final JdbcClient jdbcClient;

    public JdbcResourceChunkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void replaceAllForResource(UUID resourceId, List<ResourceChunk> chunks) {
        lockForIndexing(resourceId);
        if (chunks.stream().anyMatch(chunk -> !resourceId.equals(chunk.resourceId()))) {
            throw new IllegalArgumentException("Chunk resource does not match replacement target");
        }
        jdbcClient.sql("DELETE FROM resource_chunks WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .update();

        for (ResourceChunk chunk : chunks) {
            jdbcClient.sql("""
                            INSERT INTO resource_chunks (
                                id,
                                resource_id,
                                course_id,
                                chunk_index,
                                start_offset,
                                end_offset,
                                content_text,
                                content_sha256,
                                file_name,
                                created_at, locator_kind, locator_number, locator_label, source_sha256, parser_version
                            ) VALUES (
                                :id,
                                :resourceId,
                                :courseId,
                                :chunkIndex,
                                :startOffset,
                                :endOffset,
                                :contentText,
                                :contentSha256,
                                :fileName,
                                :createdAt, :locatorKind, :locatorNumber, :locatorLabel, :sourceSha256, :parserVersion
                            )
                            """)
                    .param("id", chunk.id())
                    .param("resourceId", chunk.resourceId())
                    .param("courseId", chunk.courseId())
                    .param("chunkIndex", chunk.chunkIndex())
                    .param("startOffset", chunk.startOffset())
                    .param("endOffset", chunk.endOffset())
                    .param("contentText", chunk.contentText())
                    .param("contentSha256", chunk.contentSha256())
                    .param("fileName", chunk.fileName())
                    .param("createdAt", Timestamp.from(chunk.createdAt()))
                    .param("locatorKind", chunk.locatorKind())
                    .param("locatorNumber", chunk.locatorNumber())
                    .param("locatorLabel", chunk.locatorLabel())
                    .param("sourceSha256", chunk.sourceSha256())
                    .param("parserVersion", chunk.parserVersion())
                    .update();
        }
    }

    @Override
    @Transactional
    public void deleteByResourceId(UUID resourceId) {
        lockResource(resourceId);
        jdbcClient.sql("""
                UPDATE resource_index_lifecycle SET deleted=TRUE, generation=generation+1, status='DELETED',
                    course_id=NULL, study_server_id=NULL, source_sha256=NULL, parser_version=NULL, file_name=NULL, signals='',
                    source_event_id=NULL,source_revision=0,job_attempts=0,job_lease_id=NULL,job_lease_until=NULL,job_retry_at=NULL
                WHERE resource_id=:resourceId
                """)
                .param("resourceId", resourceId).update();
        clearChunks(resourceId);
    }

    @Override
    @Transactional
    public void purgeByResourceId(UUID resourceId) {
        requireLive(lockResource(resourceId));
        jdbcClient.sql("UPDATE resource_index_lifecycle SET generation=generation+1, status='PENDING', source_sha256=NULL, signals='',source_event_id=NULL,source_revision=0,job_lease_id=NULL,job_lease_until=NULL,job_retry_at=NULL WHERE resource_id=:resourceId")
                .param("resourceId", resourceId).update();
        clearChunks(resourceId);
    }

    private void clearChunks(UUID resourceId) {
        jdbcClient.sql("DELETE FROM resource_chunks WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .update();
    }

    private boolean lockResource(UUID resourceId) {
        // The row survives content deletion. Concurrent first ingestion/deletion also serialize.
        jdbcClient.sql("INSERT INTO resource_index_lifecycle (resource_id) VALUES (:resourceId) ON CONFLICT DO NOTHING")
                .param("resourceId", resourceId).update();
        return jdbcClient.sql("SELECT deleted FROM resource_index_lifecycle WHERE resource_id=:resourceId FOR UPDATE")
                .param("resourceId", resourceId).query(Boolean.class).single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForIndexing(UUID resourceId) {
        requireLive(lockResource(resourceId));
    }

    private static void requireLive(boolean deleted) {
        if (deleted) throw new ResponseStatusException(HttpStatus.CONFLICT, "Resource index is permanently deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResourceChunk> findById(UUID chunkId) {
        return jdbcClient.sql("""
                        SELECT id, resource_id, course_id, chunk_index, start_offset, end_offset,
                               content_text, content_sha256, file_name, created_at, locator_kind, locator_number, locator_label, source_sha256, parser_version,
                               (SELECT signals FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS extraction_signals,
                               (SELECT study_server_id FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS study_server_id,
                               (SELECT source_revision FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS source_revision
                        FROM resource_chunks
                        WHERE id = :chunkId
                        """)
                .param("chunkId", chunkId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceChunk> findByResourceId(UUID resourceId) {
        return jdbcClient.sql("""
                        SELECT id, resource_id, course_id, chunk_index, start_offset, end_offset,
                               content_text, content_sha256, file_name, created_at, locator_kind, locator_number, locator_label, source_sha256, parser_version,
                               (SELECT signals FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS extraction_signals,
                               (SELECT study_server_id FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS study_server_id,
                               (SELECT source_revision FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS source_revision
                        FROM resource_chunks
                        WHERE resource_id = :resourceId
                        ORDER BY chunk_index ASC
                        """)
                .param("resourceId", resourceId)
                .query(this::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceChunk> findByCourseId(UUID courseId) {
        return jdbcClient.sql("""
                        SELECT id, resource_id, course_id, chunk_index, start_offset, end_offset,
                               content_text, content_sha256, file_name, created_at, locator_kind, locator_number, locator_label, source_sha256, parser_version,
                               (SELECT signals FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS extraction_signals,
                               (SELECT study_server_id FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS study_server_id,
                               (SELECT source_revision FROM resource_index_lifecycle WHERE resource_id=resource_chunks.resource_id) AS source_revision
                        FROM resource_chunks
                        WHERE course_id = :courseId
                        ORDER BY resource_id ASC, chunk_index ASC
                        """)
                .param("courseId", courseId)
                .query(this::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public int countByResourceId(UUID resourceId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM resource_chunks WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    private ResourceChunk mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Instant instant = createdAt == null ? Instant.EPOCH : createdAt.toInstant();
        return new ResourceChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("resource_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getInt("chunk_index"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getString("content_text"),
                rs.getString("content_sha256"),
                rs.getString("file_name"),
                instant,
                rs.getString("locator_kind"),
                rs.getObject("locator_number", Integer.class),
                rs.getString("locator_label"),
                rs.getString("source_sha256"),
                rs.getString("parser_version"),
                rs.getString("extraction_signals") == null || rs.getString("extraction_signals").isBlank()
                        ? java.util.Set.of() : java.util.Set.of(rs.getString("extraction_signals").split(",")),
                com.chanter.agent.domain.ResourceSourceScope.course(rs.getObject("study_server_id", UUID.class), rs.getLong("source_revision"))
        );
    }
}
