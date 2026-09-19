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
    public EmbeddingVersionStore(JdbcClient jdbc) { this.jdbc=jdbc; }
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
    }
    @Transactional public void initializeDefault(EmbeddingModel model) {
        register(model);
        jdbc.sql("UPDATE embedding_control SET active_model_id=:id WHERE id=1 AND active_model_id IS NULL").param("id",model.id()).update();
    }
    public EmbeddingModel active() {
        String id=jdbc.sql("SELECT active_model_id FROM embedding_control WHERE id=1").query(String.class).single();
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
        jdbc.sql("UPDATE embedding_control SET candidate_model_id=:id WHERE id=1").param("id",id).update();
    }
    @Transactional public void activate(String id) {
        lock();
        String staged=jdbc.sql("SELECT candidate_model_id FROM embedding_control WHERE id=1").query(String.class).single();
        if(!id.equals(staged)) throw new IllegalStateException("Model is not the staged candidate");
        requireCoverage(id);
        jdbc.sql("UPDATE embedding_control SET previous_model_id=active_model_id,active_model_id=:id,candidate_model_id=NULL WHERE id=1")
                .param("id",id).update();
    }
    @Transactional public void rollback() {
        lock();
        String previous=jdbc.sql("SELECT previous_model_id FROM embedding_control WHERE id=1").query(String.class).single();
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
    private void requireCoverage(String id) {
        if(missing(id)!=0) throw new IllegalStateException("Embedding version is missing current chunks");
    }
    public void lock() { jdbc.sql("SELECT id FROM embedding_control WHERE id=1 FOR UPDATE").query(Integer.class).single(); }
}
