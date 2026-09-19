package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import com.chanter.agent.application.*;
import com.chanter.agent.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest @ActiveProfiles("test")
class EmbeddingVersionStoreTest {
    @Autowired JdbcClient jdbc;
    @Autowired ResourceIngestionService ingestion;
    @Autowired ResourceIndexStore index;
    @Autowired ResourceChunkEmbeddingRepository vectors;
    @Autowired EmbeddingVersionStore versions;
    UUID course, resource;

    @BeforeEach void reset() {
        jdbc.sql("DELETE FROM resource_chunks").update();
        jdbc.sql("DELETE FROM resource_index_lifecycle").update();
        jdbc.sql("UPDATE embedding_control SET active_model_id='hashing-v1-384',candidate_model_id=NULL,previous_model_id=NULL").update();
        course=UUID.randomUUID(); resource=UUID.randomUUID();
    }
    @org.junit.jupiter.api.AfterEach void clearCandidate() {
        jdbc.sql("UPDATE embedding_control SET active_model_id='hashing-v1-384',candidate_model_id=NULL,previous_model_id=NULL").update();
    }

    @Test void candidateActivationNeedsFullCoverageAndBackfillPreservesPreviousVectors() {
        ingestion.ingest(course,resource,"notes.txt","A current source document.".getBytes());
        String previous=versions.active().id();
        var next=new EmbeddingModel("fixture-next","test","fixture","v2",8);
        versions.register(next); versions.stage(next.id());
        assertThatThrownBy(() -> versions.activate(next.id())).isInstanceOf(IllegalStateException.class);
        var snapshot=index.snapshot(resource);
        var prepared=snapshot.chunks().stream().map(chunk -> new ResourceChunkEmbedding(chunk.id(),resource,course,
                next.id(),8,new float[]{1,0,0,0,0,0,0,0},Instant.now())).toList();
        index.completeBackfill(resource,snapshot,prepared);
        versions.activate(next.id());
        assertThat(versions.active().id()).isEqualTo(next.id());
        assertThat(jdbc.sql("SELECT COUNT(DISTINCT model_id) FROM resource_chunk_embeddings WHERE resource_id=:id")
                .param("id",resource).query(Integer.class).single()).isEqualTo(2);
        versions.rollback();
        assertThat(versions.active().id()).isEqualTo(previous);
    }

    @Test void modelIdentityAndDimensionsCannotBeReusedForAnotherCoordinateSpace() {
        var model=new EmbeddingModel("fixture-identity","test","fixture","v1",8);
        versions.register(model);
        assertThatThrownBy(() -> versions.register(new EmbeddingModel(model.id(),"test","fixture","v2",16)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void terminalDeletionRejectsPreparedCandidateAndRemovesEveryModel() {
        ingestion.ingest(course,resource,"notes.txt","Current content.".getBytes());
        var next=new EmbeddingModel("fixture-deletion","test","fixture","v3",8);
        versions.register(next); versions.stage(next.id());
        var snapshot=index.snapshot(resource);
        ingestion.deleteByResourceId(resource);
        var prepared=snapshot.chunks().stream().map(chunk -> new ResourceChunkEmbedding(chunk.id(),resource,course,
                next.id(),8,new float[]{1,0,0,0,0,0,0,0},Instant.now())).toList();
        assertThatThrownBy(() -> index.completeBackfill(resource,snapshot,prepared)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM resource_chunk_embeddings WHERE resource_id=:id")
                .param("id",resource).query(Integer.class).single()).isZero();
    }
    @Test void anotherRotationMustDiscardOldRetainedCoordinatesExplicitly() {
        String original=versions.active().id();
        versions.register(new EmbeddingModel("rotation-one","test","fixture","r1",8));
        versions.register(new EmbeddingModel("rotation-two","test","fixture","r2",8));
        versions.stage("rotation-one");versions.activate("rotation-one");
        versions.stage("rotation-two");
        assertThatThrownBy(()->versions.activate("rotation-two")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(versions::rollback).isInstanceOf(IllegalStateException.class);
        versions.discard(original);versions.activate("rotation-two");
        assertThat(versions.state().previous()).isEqualTo("rotation-one");
        versions.rollback();versions.discard("rotation-two");
        versions.stage(original);versions.activate(original);versions.discard("rotation-one");
    }
}
