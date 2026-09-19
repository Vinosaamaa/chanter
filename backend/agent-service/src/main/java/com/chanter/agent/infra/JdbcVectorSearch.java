package com.chanter.agent.infra;

import com.chanter.agent.application.VectorRetrievalService.RankedChunk;
import com.chanter.agent.domain.EmbeddingModel;
import com.chanter.agent.domain.ResourceSourceScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Authorization joins, distance ranking and bounded result materialization happen in the datastore. */
@Repository
public class JdbcVectorSearch {
    public record AuthorizedResource(UUID resourceId, UUID studyServerId, UUID cohortId, String sourceSha256) {
        public AuthorizedResource {
            if(resourceId==null || studyServerId==null || sourceSha256==null || !sourceSha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Current resource identity, scope and checksum are required");
            }
        }
    }
    private final JdbcClient jdbc;
    private final boolean postgres;
    public JdbcVectorSearch(JdbcClient jdbc, javax.sql.DataSource dataSource) throws java.sql.SQLException {
        this.jdbc=jdbc;
        try(var connection=dataSource.getConnection()) { postgres=connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"); }
    }

    @Transactional(readOnly=true)
    public List<RankedChunk> nearest(EmbeddingModel model, UUID course, List<AuthorizedResource> authorized,
            float[] query, int limit, double minimumScore) {
        if(authorized.isEmpty()) return List.of();
        var statement=statement(model,course,authorized,query,limit,minimumScore);
        configureQuery();
        return jdbc.sql(statement.sql()).params(statement.parameters()).query((rs,row)->new RankedChunk(
                rs.getObject("id",UUID.class),rs.getObject("resource_id",UUID.class),rs.getObject("course_id",UUID.class),
                rs.getInt("chunk_index"),rs.getInt("start_offset"),rs.getInt("end_offset"),rs.getString("content_text"),
                rs.getString("file_name"),rs.getDouble("score"),rs.getString("model_id"),rs.getString("locator_kind"),
                rs.getObject("locator_number",Integer.class),rs.getString("locator_label"),rs.getString("source_sha256"),
                rs.getString("parser_version"),rs.getString("signals").isBlank()?Set.of():Set.of(rs.getString("signals").split(",")),
                new ResourceSourceScope(rs.getObject("study_server_id",UUID.class),rs.getObject("cohort_id",UUID.class),"und",
                        rs.getObject("cohort_id")==null?"COURSE":"COHORT",rs.getLong("source_revision")))).list();
    }
    @Transactional(readOnly=true)
    String explain(EmbeddingModel model,UUID course,List<AuthorizedResource> authorized,float[] query) {
        if(!postgres || authorized.isEmpty()) throw new IllegalArgumentException("PostgreSQL plan requires an authorization fixture");
        var statement=statement(model,course,authorized,query,5,0.35);
        configureQuery();
        return jdbc.sql("EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) "+statement.sql()).params(statement.parameters()).query(String.class).single();
    }
    private void configureQuery() {
        if(postgres) {
            jdbc.sql("SELECT set_config('hnsw.iterative_scan','strict_order',true)").query(String.class).single();
            jdbc.sql("SELECT set_config('statement_timeout','2000',true)").query(String.class).single();
            jdbc.sql("SELECT set_config('plan_cache_mode','force_custom_plan',true)").query(String.class).single();
        }
    }
    private record Statement(String sql,Map<String,Object> parameters) {}
    private Statement statement(EmbeddingModel model,UUID course,List<AuthorizedResource> authorized,
            float[] query,int limit,double minimumScore) {
        if(authorized.size()>5000 || course==null || limit<1 || limit>50 || !Double.isFinite(minimumScore)
                || minimumScore<0 || minimumScore>1) throw new IllegalArgumentException("Invalid bounded vector query");
        String vector=VectorValue.encode(query,model.dimensions());
        Map<String,Object> params=new LinkedHashMap<>();
        List<String> rows=new ArrayList<>();
        for(int i=0;i<authorized.size();i++) {
            var item=authorized.get(i);
            rows.add("(CAST(:id"+i+" AS UUID),CAST(:server"+i+" AS UUID),CAST(:cohort"+i+" AS UUID),CAST(:sha"+i+" AS VARCHAR(64)))");
            params.put("id"+i,item.resourceId()); params.put("server"+i,item.studyServerId());
            params.put("cohort"+i,item.cohortId()); params.put("sha"+i,item.sourceSha256());
        }
        params.put("course",course); params.put("model",model.id()); params.put("dimensions",model.dimensions());
        params.put("vector",vector); params.put("limit",limit); params.put("minimum",minimumScore);
        String distance=postgres ? "e.embedding::public.vector("+model.dimensions()+") OPERATOR(public.<=>) CAST(:vector AS public.vector)"
                : "vector_distance(e.embedding,:vector)";
        String sql="""
            WITH authorized(resource_id,study_server_id,cohort_id,source_sha256) AS (VALUES %s),
            nearest AS (
                SELECT c.*,l.study_server_id,l.cohort_id,l.source_revision,l.signals,e.model_id,1-(%s) AS score
                FROM resource_chunk_embeddings e
                JOIN resource_chunks c ON c.id=e.chunk_id AND c.resource_id=e.resource_id AND c.course_id=e.course_id
                JOIN resource_index_lifecycle l ON l.resource_id=c.resource_id
                JOIN authorized a ON a.resource_id=c.resource_id AND a.study_server_id=l.study_server_id
                    AND (a.cohort_id=l.cohort_id OR (a.cohort_id IS NULL AND l.cohort_id IS NULL))
                    AND a.source_sha256=l.source_sha256 AND a.source_sha256=c.source_sha256
                WHERE e.model_id=:model AND e.dimensions=:dimensions AND c.course_id=:course AND l.course_id=:course
                    AND l.deleted=FALSE AND l.status='READY' AND c.parser_version=l.parser_version
                    AND e.model_id=(SELECT active_model_id FROM embedding_control WHERE id=1)
                ORDER BY %s LIMIT :limit
            ) SELECT * FROM nearest WHERE score>=:minimum ORDER BY score DESC,id
            """.formatted(String.join(",",rows),distance,distance);
        return new Statement(sql,params);
    }
}
