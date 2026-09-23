package com.chanter.agent.lifecycle;

import com.chanter.agent.application.ResourceChunkRepository;
import com.chanter.agent.application.GroundedSupportQuestionService.NativeEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared owning erasure for canonical reapply and authenticated media deletion commands. */
@Component
public final class AgentResourceCleanup {
    private final ResourceChunkRepository chunks;
    private final AnswerRetractions retractions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AgentResourceCleanup(ResourceChunkRepository chunks,AnswerRetractions retractions,JdbcTemplate jdbc,ObjectMapper mapper) {
        this.chunks=chunks;this.retractions=retractions;this.jdbc=jdbc;this.mapper=mapper;
    }
    public void erase(UUID resource) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Resource erasure requires transaction");
        jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        chunks.deleteByResourceId(resource);retractions.resource(resource);
        jdbc.update("DELETE FROM study_assistant_grants WHERE grant_type='COURSE_RESOURCE' AND grant_target_id=?",resource);
        UUID after=new UUID(0,0);
        while(true) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Agent resource cleanup interrupted");
            var rows=jdbc.query("SELECT id,evidence_json FROM native_companion_requests WHERE evidence_json IS NOT NULL AND id>? ORDER BY id LIMIT 256",
                    (rs,n)->new Evidence(rs.getObject(1,UUID.class),rs.getString(2)),after);
            if(rows.isEmpty()) return;
            for(var row:rows) {
                boolean erase;
                try {var evidence=mapper.readValue(row.json(),NativeEvidence.class);erase=evidence==null || evidence.resourceIds().contains(resource);}
                catch(java.io.IOException | IllegalArgumentException malformed) {erase=true;}
                if(erase) jdbc.update("UPDATE native_companion_requests SET evidence_json=NULL,outcome=CASE WHEN outcome IN ('ISSUED','ACCEPTING') THEN 'REJECTED' ELSE outcome END WHERE id=?",row.id());
            }
            after=rows.getLast().id();
        }
    }
    private record Evidence(UUID id,String json) { }
}
