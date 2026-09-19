package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Discards demo coordinates. Current source chunks remain available for durable semantic rebuilding. */
public class V13__scoped_embedding_versions extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        boolean postgres=connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
        if (!postgres && !"true".equals(context.getConfiguration().getPlaceholders().get("testVectorDatabase"))) {
            throw new IllegalStateException("Production vector retrieval requires PostgreSQL with pgvector");
        }
        try(var sql=connection.createStatement()) {
            if(postgres) {
                try(var extension=sql.executeQuery("SELECT extversion FROM pg_extension WHERE extname='vector'")) {
                    if(!extension.next()) throw new IllegalStateException("The PostgreSQL owner must install pgvector before agent migration");
                }
            }
            sql.execute("""
                CREATE TABLE embedding_models (
                    id VARCHAR(160) PRIMARY KEY, provider VARCHAR(32) NOT NULL, model VARCHAR(200) NOT NULL,
                    revision VARCHAR(160) NOT NULL, dimensions INTEGER NOT NULL CHECK(dimensions BETWEEN 8 AND 2000),
                    UNIQUE(id,dimensions))
                """);
            sql.execute("""
                CREATE TABLE embedding_control (
                    id INTEGER PRIMARY KEY CHECK(id=1), active_model_id VARCHAR(160) REFERENCES embedding_models(id),
                    candidate_model_id VARCHAR(160) REFERENCES embedding_models(id), previous_model_id VARCHAR(160) REFERENCES embedding_models(id))
                """);
            sql.execute("INSERT INTO embedding_control(id) VALUES(1)");
            sql.execute("DROP TABLE resource_chunk_embeddings");
            sql.execute("ALTER TABLE resource_chunks ADD CONSTRAINT uq_chunk_resource_course UNIQUE(id,resource_id,course_id)");
            sql.execute("""
                CREATE TABLE resource_chunk_embeddings (
                    chunk_id UUID NOT NULL, resource_id UUID NOT NULL, course_id UUID NOT NULL,
                    model_id VARCHAR(160) NOT NULL, dimensions INTEGER NOT NULL,
                    embedding %s NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    PRIMARY KEY(chunk_id,model_id),
                    FOREIGN KEY(chunk_id,resource_id,course_id) REFERENCES resource_chunks(id,resource_id,course_id) ON DELETE CASCADE,
                    FOREIGN KEY(model_id,dimensions) REFERENCES embedding_models(id,dimensions)%s)
                """.formatted(postgres ? "public.vector" : "VARCHAR(65536)",
                        postgres ? ", CHECK(public.vector_dims(embedding)=dimensions)" : ""));
            sql.execute("CREATE INDEX idx_embedding_scope ON resource_chunk_embeddings(course_id,resource_id,model_id)");
            sql.execute("ALTER TABLE resource_index_lifecycle ADD COLUMN cohort_id UUID");
            sql.execute("""
                CREATE TABLE embedding_rebuild_jobs (
                    resource_id UUID NOT NULL REFERENCES resource_index_lifecycle(resource_id), model_id VARCHAR(160) NOT NULL REFERENCES embedding_models(id),
                    generation BIGINT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
                    lease_id UUID, lease_until TIMESTAMP WITH TIME ZONE, retry_at TIMESTAMP WITH TIME ZONE,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING', PRIMARY KEY(resource_id,model_id))
                """);
            if(!postgres) sql.execute("CREATE ALIAS IF NOT EXISTS vector_distance FOR 'com.chanter.agent.infra.VectorValue.testDistance'");
        }
    }
}
