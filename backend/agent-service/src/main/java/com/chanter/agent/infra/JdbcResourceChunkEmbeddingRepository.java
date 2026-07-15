package com.chanter.agent.infra;

import com.chanter.agent.application.EmbeddingCodec;
import com.chanter.agent.application.ResourceChunkEmbeddingRepository;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcResourceChunkEmbeddingRepository implements ResourceChunkEmbeddingRepository {

    private final JdbcClient jdbcClient;

    public JdbcResourceChunkEmbeddingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void replaceAllForResource(UUID resourceId, List<ResourceChunkEmbedding> embeddings) {
        jdbcClient.sql("DELETE FROM resource_chunk_embeddings WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .update();

        for (ResourceChunkEmbedding embedding : embeddings) {
            jdbcClient.sql("""
                            INSERT INTO resource_chunk_embeddings (
                                chunk_id,
                                resource_id,
                                course_id,
                                model_id,
                                dimensions,
                                embedding,
                                created_at
                            ) VALUES (
                                :chunkId,
                                :resourceId,
                                :courseId,
                                :modelId,
                                :dimensions,
                                :embedding,
                                :createdAt
                            )
                            """)
                    .param("chunkId", embedding.chunkId())
                    .param("resourceId", embedding.resourceId())
                    .param("courseId", embedding.courseId())
                    .param("modelId", embedding.modelId())
                    .param("dimensions", embedding.dimensions())
                    .param("embedding", EmbeddingCodec.toBytes(embedding.vector()))
                    .param("createdAt", Timestamp.from(embedding.createdAt()))
                    .update();
        }
    }

    @Override
    @Transactional
    public void deleteByResourceId(UUID resourceId) {
        jdbcClient.sql("DELETE FROM resource_chunk_embeddings WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceChunkEmbedding> findByResourceIds(Collection<UUID> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT chunk_id, resource_id, course_id, model_id, dimensions, embedding, created_at
                        FROM resource_chunk_embeddings
                        WHERE resource_id IN (:resourceIds)
                        """)
                .param("resourceIds", resourceIds)
                .query(this::mapRow)
                .list();
    }

    private ResourceChunkEmbedding mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        int dimensions = rs.getInt("dimensions");
        byte[] bytes = rs.getBytes("embedding");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Instant instant = createdAt == null ? Instant.EPOCH : createdAt.toInstant();
        return new ResourceChunkEmbedding(
                rs.getObject("chunk_id", UUID.class),
                rs.getObject("resource_id", UUID.class),
                rs.getObject("course_id", UUID.class),
                rs.getString("model_id"),
                dimensions,
                EmbeddingCodec.fromBytes(bytes, dimensions),
                instant
        );
    }
}
