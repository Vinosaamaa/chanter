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

@Repository
public class JdbcResourceChunkRepository implements ResourceChunkRepository {

    private final JdbcClient jdbcClient;

    public JdbcResourceChunkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void replaceAllForResource(UUID resourceId, List<ResourceChunk> chunks) {
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
                                created_at
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
                                :createdAt
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
                    .update();
        }
    }

    @Override
    @Transactional
    public void deleteByResourceId(UUID resourceId) {
        jdbcClient.sql("DELETE FROM resource_chunks WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResourceChunk> findById(UUID chunkId) {
        return jdbcClient.sql("""
                        SELECT id, resource_id, course_id, chunk_index, start_offset, end_offset,
                               content_text, content_sha256, file_name, created_at
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
                               content_text, content_sha256, file_name, created_at
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
                               content_text, content_sha256, file_name, created_at
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
                instant
        );
    }
}
