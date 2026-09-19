package com.chanter.agent.application;

import com.chanter.agent.domain.EmbeddingModel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Serializes coordinate-space changes; no model inference executes under this lock. */
@Repository
public class EmbeddingVersionStore {
    private final JdbcClient jdbc;
    private final boolean postgres;
    public EmbeddingVersionStore(JdbcClient jdbc,javax.sql.DataSource dataSource) throws java.sql.SQLException {
        this.jdbc=jdbc;
        try(var connection=dataSource.getConnection()){postgres=connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");}
    }
    @Transactional public void register(EmbeddingModel model) {
        lock();
        var existing=find(model.id());
        if(existing!=null) {
            if(!existing.equals(model)) throw new IllegalStateException("Embedding model identity cannot change");
            return;
        }
        jdbc.sql("INSERT INTO embedding_models(id,provider,model,revision,dimensions) VALUES(:id,:provider,:model,:revision,:dimensions)")
                .param("id",model.id()).param("provider",model.provider()).param("model",model.model())
                .param("revision",model.revision()).param("dimensions",model.dimensions()).update();
        if(postgres) {
            // IDs are constrained to safe ASCII by EmbeddingModel. Each partial index has one fixed dimension.
            jdbc.sql("CREATE INDEX "+indexName(model.id())+" ON resource_chunk_embeddings USING hnsw ((embedding::public.vector("+model.dimensions()
                    +")) public.vector_cosine_ops) WHERE model_id='"+model.id()+"'").update();
        }
    }
    @Transactional public void initializeDefault(EmbeddingModel model) {
        register(model);
        jdbc.sql("UPDATE embedding_control SET active_model_id=:id WHERE id=1 AND active_model_id IS NULL").param("id",model.id()).update();
    }
    public EmbeddingModel active() {
        String id=jdbc.sql("SELECT active_model_id FROM embedding_control WHERE id=1").query(String.class).optional().orElse(null);
        if(id==null) throw new IllegalStateException("No active semantic embedding model");
        return find(id);
    }
    public EmbeddingModel find(String id) {
        return jdbc.sql("SELECT id,provider,model,revision,dimensions FROM embedding_models WHERE id=:id").param("id",id)
                .query((rs,row)->new EmbeddingModel(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)))
                .optional().orElse(null);
    }
    public Set<String> writableIds() {
        return jdbc.sql("SELECT active_model_id,candidate_model_id,previous_model_id FROM embedding_control WHERE id=1")
                .query((rs,row)->{var ids=new LinkedHashSet<String>();for(int i=1;i<=3;i++)if(rs.getString(i)!=null)ids.add(rs.getString(i));return Set.copyOf(ids);}).single();
    }
    @Transactional public void stage(String id) {
        lock();
        if(find(id)==null) throw new IllegalArgumentException("Unknown embedding model");
        if(id.equals(active().id())) throw new IllegalStateException("Model is already active");
        String candidate=jdbc.sql("SELECT candidate_model_id FROM embedding_control WHERE id=1").query(String.class).optional().orElse(null);
        if(candidate!=null && !candidate.equals(id)) throw new IllegalStateException("Discard the existing candidate first");
        jdbc.sql("UPDATE embedding_control SET candidate_model_id=:id WHERE id=1").param("id",id).update();
    }
    @Transactional public void activate(String id) {
        lock();
        String staged=jdbc.sql("SELECT candidate_model_id FROM embedding_control WHERE id=1").query(String.class).optional().orElse(null);
        if(!id.equals(staged)) throw new IllegalStateException("Model is not the staged candidate");
        String previous=state().previous();
        if(previous!=null && !previous.equals(id)) throw new IllegalStateException("Discard the older rollback version before activation");
        requireCoverage(id);
        jdbc.sql("UPDATE embedding_control SET previous_model_id=active_model_id,active_model_id=:id,candidate_model_id=NULL WHERE id=1")
                .param("id",id).update();
    }
    @Transactional public void rollback() {
        lock();
        if(state().candidate()!=null) throw new IllegalStateException("Discard the staged candidate before rollback");
        String previous=jdbc.sql("SELECT previous_model_id FROM embedding_control WHERE id=1").query(String.class).optional().orElse(null);
        if(previous==null) throw new IllegalStateException("No retained embedding version");
        requireCoverage(previous);
        String current=active().id();
        jdbc.sql("UPDATE embedding_control SET active_model_id=:previous,previous_model_id=:current,candidate_model_id=NULL WHERE id=1")
                .param("previous",previous).param("current",current).update();
    }
    public long missing(String id) {
        return jdbc.sql("""
            SELECT COUNT(*) FROM resource_chunks c JOIN resource_index_lifecycle l ON l.resource_id=c.resource_id
            WHERE l.deleted=FALSE AND l.status='READY'
              AND NOT EXISTS(SELECT 1 FROM resource_chunk_embeddings e WHERE e.chunk_id=c.id AND e.model_id=:model)
            """).param("model",id).query(Long.class).single();
    }
    public record State(String active,String candidate,String previous) {}
    public record Progress(EmbeddingModel model,long missingChunks,long indexedChunks,long failedResources) {}
    public State state() {
        return jdbc.sql("SELECT active_model_id,candidate_model_id,previous_model_id FROM embedding_control WHERE id=1")
                .query((rs,row)->new State(rs.getString(1),rs.getString(2),rs.getString(3))).single();
    }
    public List<Progress> progress() {
        return jdbc.sql("SELECT id,provider,model,revision,dimensions FROM embedding_models ORDER BY id")
                .query((rs,row)->new EmbeddingModel(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)))
                .list().stream().map(model->new Progress(model,missing(model.id()),
                    jdbc.sql("SELECT COUNT(*) FROM resource_chunk_embeddings WHERE model_id=:id").param("id",model.id()).query(Long.class).single(),
                    jdbc.sql("SELECT COUNT(*) FROM embedding_rebuild_jobs j JOIN resource_index_lifecycle l ON l.resource_id=j.resource_id WHERE j.model_id=:id AND j.status='FAILED' AND j.generation=l.generation AND l.deleted=FALSE")
                        .param("id",model.id()).query(Long.class).single())).toList();
    }
    @Transactional public void discard(String id) {
        lock();
        if(id.equals(active().id())) throw new IllegalStateException("The serving version cannot be discarded");
        jdbc.sql("UPDATE embedding_control SET candidate_model_id=CASE WHEN candidate_model_id=:id THEN NULL ELSE candidate_model_id END, previous_model_id=CASE WHEN previous_model_id=:id THEN NULL ELSE previous_model_id END WHERE id=1")
                .param("id",id).update();
        jdbc.sql("DELETE FROM embedding_rebuild_jobs WHERE model_id=:id").param("id",id).update();
        jdbc.sql("DELETE FROM resource_chunk_embeddings WHERE model_id=:id").param("id",id).update();
        // Keep immutable catalog identity and its empty index; configuration may explicitly stage it again.
    }
    private static String indexName(String id) {
        try { return "idx_vector_"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,24); }
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void requireCoverage(String id) {
        if(missing(id)!=0) throw new IllegalStateException("Embedding version is missing current chunks");
    }
    public void lock() { jdbc.sql("SELECT id FROM embedding_control WHERE id=1 FOR UPDATE").query(Integer.class).single(); }
}
