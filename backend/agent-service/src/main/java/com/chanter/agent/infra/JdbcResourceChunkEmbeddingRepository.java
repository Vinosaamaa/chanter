package com.chanter.agent.infra;


import com.chanter.agent.application.ResourceChunkEmbeddingRepository;
import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.sql.Timestamp;


import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcResourceChunkEmbeddingRepository implements ResourceChunkEmbeddingRepository {

    private final JdbcClient jdbcClient;
    private final boolean postgres;

    public JdbcResourceChunkEmbeddingRepository(JdbcClient jdbcClient, javax.sql.DataSource dataSource) throws java.sql.SQLException {
        this.jdbcClient = jdbcClient;
        try(var connection=dataSource.getConnection()) { postgres=connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"); }
    }

    @Override
    @Transactional
    public void replaceAllForResource(UUID resourceId, List<ResourceChunkEmbedding> embeddings) {
        jdbcClient.sql("DELETE FROM resource_chunk_embeddings WHERE resource_id = :resourceId")
                .param("resourceId", resourceId)
                .update();
        insert(embeddings);
    }

    @Override @Transactional
    public void replaceModelsForResource(UUID resourceId, List<ResourceChunkEmbedding> embeddings) {
        for(String model:embeddings.stream().map(ResourceChunkEmbedding::modelId).distinct().toList()) {
            jdbcClient.sql("DELETE FROM resource_chunk_embeddings WHERE resource_id=:resource AND model_id=:model")
                    .param("resource",resourceId).param("model",model).update();
        }
        insert(embeddings);
    }

    private void insert(List<ResourceChunkEmbedding> embeddings) {
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
                                %s,
                                :createdAt
                            )
                            """.formatted(postgres ? "CAST(:embedding AS public.vector)" : ":embedding"))
                    .param("chunkId", embedding.chunkId())
                    .param("resourceId", embedding.resourceId())
                    .param("courseId", embedding.courseId())
                    .param("modelId", embedding.modelId())
                    .param("dimensions", embedding.dimensions())
                    .param("embedding", VectorValue.encode(embedding.vector(), embedding.dimensions()))
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

}
