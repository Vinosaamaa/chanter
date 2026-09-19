package com.chanter.agent.application;

import com.chanter.agent.domain.ResourceChunkEmbedding;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** One durable lease per resource/model. Preparation happens outside all transactions. */
@Repository
public class EmbeddingRebuildJobs {
    public record Job(UUID resourceId,String modelId,UUID leaseId,ResourceIndexStore.Attempt snapshot) {}
    private final JdbcClient jdbc;
    private final ResourceIndexStore index;
    private final EmbeddingVersionStore versions;
    private final Clock clock;
    private final boolean postgres;
    public EmbeddingRebuildJobs(JdbcClient jdbc,ResourceIndexStore index,EmbeddingVersionStore versions,Clock clock,
            javax.sql.DataSource dataSource) throws java.sql.SQLException {
        this.jdbc=jdbc;this.index=index;this.versions=versions;this.clock=clock;
        try(var connection=dataSource.getConnection()){postgres=connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");}
    }
    private OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
    @Transactional public Optional<Job> claim() {
        var now=now();
        jdbc.sql("UPDATE embedding_rebuild_jobs SET status='FAILED',lease_id=NULL,lease_until=NULL WHERE status='PROCESSING' AND attempts>=5 AND lease_until<=:now")
                .param("now",now).update();
        var models=new ArrayList<>(versions.writableIds());
        String active=versions.active().id();models.remove(active);models.addFirst(active);
        for(String model:models) {
            String sql="""
                SELECT l.resource_id FROM resource_index_lifecycle l
                LEFT JOIN embedding_rebuild_jobs j ON j.resource_id=l.resource_id AND j.model_id=:model
                WHERE l.deleted=FALSE AND l.status='READY'
                  AND EXISTS(SELECT 1 FROM resource_chunks c WHERE c.resource_id=l.resource_id
                    AND NOT EXISTS(SELECT 1 FROM resource_chunk_embeddings e WHERE e.chunk_id=c.id AND e.model_id=:model))
                  AND (j.resource_id IS NULL OR j.generation<>l.generation
                    OR (j.attempts<5 AND (j.lease_until IS NULL OR j.lease_until<=:now) AND (j.retry_at IS NULL OR j.retry_at<=:now)))
                ORDER BY l.resource_id LIMIT 1 %s
                """.formatted(postgres?"FOR UPDATE OF l SKIP LOCKED":"FOR UPDATE SKIP LOCKED");
            var resource=jdbc.sql(sql).param("model",model).param("now",now).query(UUID.class).optional();
            if(resource.isEmpty()) continue;
            UUID id=resource.get(),lease=UUID.randomUUID();
            var snapshot=index.snapshot(id);
            int updated=jdbc.sql("""
                UPDATE embedding_rebuild_jobs SET attempts=CASE WHEN generation=:generation THEN attempts+1 ELSE 1 END,
                    generation=:generation,status='PROCESSING',lease_id=:lease,lease_until=:until,retry_at=NULL
                WHERE resource_id=:id AND model_id=:model
                """).param("generation",snapshot.generation()).param("lease",lease).param("until",now.plusMinutes(30))
                    .param("id",id).param("model",model).update();
            if(updated==0) jdbc.sql("""
                INSERT INTO embedding_rebuild_jobs(resource_id,model_id,generation,attempts,status,lease_id,lease_until)
                VALUES(:id,:model,:generation,1,'PROCESSING',:lease,:until)
                """).param("id",id).param("model",model).param("generation",snapshot.generation()).param("lease",lease)
                    .param("until",now.plusMinutes(30)).update();
            return Optional.of(new Job(id,model,lease,snapshot));
        }
        return Optional.empty();
    }
    @Transactional public boolean complete(Job job,List<ResourceChunkEmbedding> prepared) {
        var current=jdbc.sql("SELECT generation,deleted,status FROM resource_index_lifecycle WHERE resource_id=:id FOR UPDATE")
                .param("id",job.resourceId()).query((rs,row)->rs.getLong(1)==job.snapshot().generation() && !rs.getBoolean(2) && "READY".equals(rs.getString(3)))
                .optional().orElse(false);
        if(!current) return false;
        versions.lock();
        if(!versions.writableIds().contains(job.modelId())) return false;
        boolean owned=jdbc.sql("""
                SELECT resource_id FROM embedding_rebuild_jobs WHERE resource_id=:id AND model_id=:model AND lease_id=:lease
                    AND generation=:generation AND status='PROCESSING' AND lease_until>:now FOR UPDATE
                """).param("id",job.resourceId()).param("model",job.modelId()).param("lease",job.leaseId())
                .param("generation",job.snapshot().generation()).param("now",now()).query(UUID.class).optional().isPresent();
        if(!owned) return false;
        if(prepared.stream().anyMatch(vector->!job.modelId().equals(vector.modelId()))) throw new IllegalArgumentException("Rebuild returned a different model");
        index.completeBackfill(job.resourceId(),job.snapshot(),prepared);
        jdbc.sql("UPDATE embedding_rebuild_jobs SET status='READY',lease_id=NULL,lease_until=NULL,retry_at=NULL WHERE resource_id=:id AND model_id=:model")
                .param("id",job.resourceId()).param("model",job.modelId()).update();
        return true;
    }
    @Transactional public void failed(Job job) {
        jdbc.sql("""
            UPDATE embedding_rebuild_jobs SET status=CASE WHEN attempts>=5 THEN 'FAILED' ELSE 'PENDING' END,
                lease_id=NULL,lease_until=NULL,retry_at=:retry
            WHERE resource_id=:id AND model_id=:model AND lease_id=:lease AND generation=:generation
            """).param("retry",now().plusSeconds(30)).param("id",job.resourceId()).param("model",job.modelId())
                .param("lease",job.leaseId()).param("generation",job.snapshot().generation()).update();
    }
    public long failedCount(String model) {
        return jdbc.sql("""
            SELECT COUNT(*) FROM embedding_rebuild_jobs j JOIN resource_index_lifecycle l ON l.resource_id=j.resource_id
            WHERE j.model_id=:model AND j.status='FAILED' AND j.generation=l.generation AND l.deleted=FALSE AND l.status='READY'
            """).param("model",model).query(Long.class).single();
    }
    @Transactional public void retryFailed(UUID resource,String model) {
        jdbc.sql("""
            UPDATE embedding_rebuild_jobs SET status='PENDING',attempts=0,retry_at=NULL,lease_id=NULL,lease_until=NULL
            WHERE resource_id=:id AND model_id=:model AND status='FAILED'
            """).param("id",resource).param("model",model).update();
    }
}
