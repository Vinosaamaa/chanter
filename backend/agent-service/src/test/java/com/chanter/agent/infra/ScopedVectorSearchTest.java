package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import com.chanter.agent.application.*;
import com.chanter.agent.domain.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest @ActiveProfiles("test")
class ScopedVectorSearchTest {
    @Autowired ResourceIngestionService ingestion;
    @Autowired ResourceChunkEmbeddingRepository embeddings;
    @Autowired EmbeddingVersionStore versions;
    @Autowired JdbcVectorSearch search;
    @Autowired JdbcClient jdbc;
    UUID course, server;

    @BeforeEach void initialize() { course=UUID.randomUUID(); server=UUID.randomUUID(); }
    private JdbcVectorSearch.AuthorizedResource resource(UUID owningCourse, UUID cohort, String text) {
        UUID id=UUID.randomUUID();
        var result=ingestion.ingest(owningCourse,id,"lesson.md",text.getBytes());
        jdbc.sql("UPDATE resource_index_lifecycle SET study_server_id=:server,cohort_id=:cohort WHERE resource_id=:id")
                .param("server",server).param("cohort",cohort).param("id",id).update();
        return new JdbcVectorSearch.AuthorizedResource(id,server,cohort,result.sourceSha256());
    }
    private float[] vector(UUID id) { return embeddings.findByResourceIds(List.of(id)).getFirst().vector(); }

    @Test void authorizationFiltersBeforeTopKAndVersionsScopesRemainCurrent() {
        var allowed=resource(course,null,"Students submit homework on Friday.");
        for(int i=0;i<12;i++)resource(course,null,"Students submit homework on Friday.");
        var query=vector(allowed.resourceId());
        assertThat(search.nearest(versions.active(),course,List.of(allowed),query,1,0.1))
                .extracting(VectorRetrievalService.RankedChunk::resourceId).containsExactly(allowed.resourceId());
        assertThat(search.nearest(versions.active(),UUID.randomUUID(),List.of(allowed),query,1,0.1)).isEmpty();
        var wrongServer=new JdbcVectorSearch.AuthorizedResource(allowed.resourceId(),UUID.randomUUID(),null,allowed.sourceSha256());
        assertThat(search.nearest(versions.active(),course,List.of(wrongServer),query,1,0.1)).isEmpty();
        var wrongCohort=new JdbcVectorSearch.AuthorizedResource(allowed.resourceId(),server,UUID.randomUUID(),allowed.sourceSha256());
        assertThat(search.nearest(versions.active(),course,List.of(wrongCohort),query,1,0.1)).isEmpty();
        var stale=new JdbcVectorSearch.AuthorizedResource(allowed.resourceId(),server,null,"0".repeat(64));
        assertThat(search.nearest(versions.active(),course,List.of(stale),query,1,0.1)).isEmpty();
        ingestion.deleteByResourceId(allowed.resourceId());
        assertThat(search.nearest(versions.active(),course,List.of(allowed),query,1,0.1)).isEmpty();
    }

    @Test void emptyAuthorizationLowScoreAndWrongDimensionNeverReturnEvidence() {
        var allowed=resource(course,UUID.randomUUID(),"A scoped lesson about homework.");
        var query=vector(allowed.resourceId());
        assertThat(search.nearest(versions.active(),course,List.of(),query,5,0.1)).isEmpty();
        for(int i=0;i<query.length;i++)query[i]=-query[i];
        assertThat(search.nearest(versions.active(),course,List.of(allowed),query,5,0.1)).isEmpty();
        assertThatThrownBy(()->search.nearest(versions.active(),course,List.of(allowed),new float[8],5,0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
