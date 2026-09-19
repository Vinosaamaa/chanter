package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.*;
import com.chanter.agent.infra.EmbeddingProviders;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:embedding-rebuild;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1") @ActiveProfiles("test")
class EmbeddingRebuildJobsTest {
    @TestConfiguration static class Models {
        @Bean @Primary EmbeddingProviders migrationModels(HashingEmbeddingClient original) {
            return new EmbeddingProviders(List.of(original,new HashingEmbeddingClient(16)),original.modelId());
        }
    }
    @Autowired ResourceIngestionService ingestion;
    @Autowired EmbeddingVersionStore versions;
    @Autowired EmbeddingRebuildJobs jobs;
    @Autowired EmbeddingPipelineService pipeline;
    @Autowired JdbcClient jdbc;
    UUID resource,course;
    final String next="hashing-v1-16";
    @BeforeEach void setup() {
        jdbc.sql("DELETE FROM embedding_rebuild_jobs").update();
        jdbc.sql("DELETE FROM resource_chunks").update(); jdbc.sql("DELETE FROM resource_index_lifecycle").update();
        jdbc.sql("UPDATE embedding_control SET active_model_id='hashing-v1-384',candidate_model_id=NULL,previous_model_id=NULL").update();
        resource=UUID.randomUUID();course=UUID.randomUUID();
        ingestion.ingest(course,resource,"guide.txt","Students submit assignments on Friday.".getBytes());
        versions.stage(next);
    }
    @Test void backgroundRebuildPreservesServingVersionAndMakesActivationAndRollbackPossible() {
        assertThat(versions.missing(next)).isEqualTo(1);
        new EmbeddingRebuildWorker(jobs,pipeline).runOnce();
        assertThat(versions.missing(next)).isZero();
        versions.activate(next); assertThat(versions.active().id()).isEqualTo(next);
        versions.rollback(); assertThat(versions.active().id()).isEqualTo("hashing-v1-384");
    }
    @Test void expiredClaimCannotPublishAfterAReplacementLease() {
        var old=jobs.claim().orElseThrow();
        var prepared=pipeline.prepareFor(old.modelId(),old.snapshot().chunks());
        jdbc.sql("UPDATE embedding_rebuild_jobs SET lease_until=:expired").param("expired",OffsetDateTime.now().minusHours(1)).update();
        var replacement=jobs.claim().orElseThrow();
        assertThat(jobs.complete(old,prepared)).isFalse();
        assertThat(jobs.complete(replacement,pipeline.prepareFor(replacement.modelId(),replacement.snapshot().chunks()))).isTrue();
    }
    @Test void replacementAndTerminalDeletionRejectStaleRebuilds() {
        var old=jobs.claim().orElseThrow();
        var prepared=pipeline.prepareFor(old.modelId(),old.snapshot().chunks());
        ingestion.ingest(course,resource,"guide.txt","A replacement source with new facts.".getBytes());
        assertThat(jobs.complete(old,prepared)).isFalse();
        var current=jobs.claim().orElseThrow();
        ingestion.deleteByResourceId(resource);
        assertThat(jobs.complete(current,pipeline.prepareFor(current.modelId(),current.snapshot().chunks()))).isFalse();
        assertThat(jobs.claim()).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM embedding_rebuild_jobs WHERE resource_id=:id").param("id",resource).query(Long.class).single()).isZero();
    }
    @Test void discardedCandidateRejectsInFlightWorkAndRemovesRetainedCoordinates() {
        var job=jobs.claim().orElseThrow();
        var prepared=pipeline.prepareFor(job.modelId(),job.snapshot().chunks());
        versions.discard(next);
        assertThat(jobs.complete(job,prepared)).isFalse();
        assertThat(versions.state().candidate()).isNull();
        assertThat(versions.progress().stream().filter(item->item.model().id().equals(next)).findFirst().orElseThrow().indexedChunks()).isZero();
        assertThatThrownBy(()->versions.discard(versions.active().id())).isInstanceOf(IllegalStateException.class);
    }
    @Test void retriesStopAfterFiveAttemptsAndRequireExplicitRetry() {
        for(int i=0;i<5;i++) {
            var job=jobs.claim().orElseThrow(); jobs.failed(job);
            jdbc.sql("UPDATE embedding_rebuild_jobs SET retry_at=NULL").update();
        }
        assertThat(jobs.claim()).isEmpty();
        assertThat(jobs.failedCount(next)).isEqualTo(1);
        jobs.retryFailed(resource,next);
        assertThat(jobs.claim()).isPresent();
    }
}
