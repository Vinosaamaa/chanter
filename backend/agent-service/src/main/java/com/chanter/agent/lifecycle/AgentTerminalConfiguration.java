package com.chanter.agent.lifecycle;

import com.chanter.agent.application.ResourceChunkRepository;
import com.chanter.agent.application.GroundedSupportQuestionService.NativeEvidence;
import com.chanter.common.lifecycle.*;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import({SourceTerminalRecoveryController.class,SourceDeletedScopeController.class})
public class AgentTerminalConfiguration {
    @Bean DeletedScopeStore agentDeletedScopeStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,TerminalReapplyStore terminal) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new DeletedScopeStore(jdbc,tx,entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean RecoveryScopeStore agentRecoveryScopeStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            DeletedScopeStore current,TerminalReapplyStore terminal,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-restore-id:${CHANTER_RECOVERY_RESTORE_ID:}}") String restore) {
        if(!recovery && !restore.isBlank()) throw new IllegalArgumentException("Restore identity requires recovery mode");
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new RecoveryScopeStore(jdbc,tx,current,recovery && !restore.isBlank() ? UUID.fromString(restore) : null,
                entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean AccountDeletionParticipant agentDeletionParticipant(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            com.chanter.common.events.DurableOutbox outbox,AccountDeletionProtocol protocol,TerminalReapplyStore terminal) {
        return new AccountDeletionParticipant("agent",new com.chanter.common.events.DurableConsumer(jdbc,new TransactionTemplate(transactions)),
                outbox,protocol,terminal,null);
    }
    @Bean TerminalReapplyStore agentTerminalStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            ExportSnapshotStore snapshots,ResourceChunkRepository chunks,com.fasterxml.jackson.databind.ObjectMapper mapper,AnswerRetractions retractions,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            org.springframework.beans.factory.ObjectProvider<RecoveryScopeStore> historical) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"agent",entry -> {
            UUID target=entry.targetId();
            if(entry.targetKind().equals("ACCOUNT")) {
                snapshots.cancelAccount(target);
                retractions.account(target);
                jdbc.update("DELETE FROM study_assistant_answer_helpful WHERE user_id=?",target);
                jdbc.update("UPDATE native_companion_requests SET evidence_json=NULL,outcome=CASE WHEN outcome IN ('ISSUED','ACCEPTING') THEN 'REJECTED' ELSE outcome END WHERE user_id=?",target);
                // Usage and shared installation attribution require their documented retention disposition.
                return TerminalReapplyStore.Cleanup.PENDING;
            }
            // At most 16 retained source snapshots; cancel them before removing their canonical content.
            snapshots.invalidateRetained();
            if(entry.targetKind().equals("RESOURCE")) {
                chunks.deleteByResourceId(target);
                retractions.resource(target);
                jdbc.update("DELETE FROM study_assistant_grants WHERE grant_type='COURSE_RESOURCE' AND grant_target_id=?",target);
                UUID after=new UUID(0,0);
                while(true) {
                    if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Agent terminal cleanup interrupted");
                    var rows=jdbc.query("SELECT id,evidence_json FROM native_companion_requests WHERE evidence_json IS NOT NULL AND id>? ORDER BY id LIMIT 256",
                            (rs,row)->new Evidence(rs.getObject(1,UUID.class),rs.getString(2)),after);
                    if(rows.isEmpty()) break;
                    for(var row:rows) {
                        boolean erase;
                        try { var evidence=mapper.readValue(row.json(),NativeEvidence.class);
                            erase=evidence==null || evidence.resourceIds().contains(target);
                        } catch(java.io.IOException | IllegalArgumentException malformed) { erase=true; }
                        if(erase) jdbc.update("UPDATE native_companion_requests SET evidence_json=NULL,outcome=CASE WHEN outcome IN ('ISSUED','ACCEPTING') THEN 'REJECTED' ELSE outcome END WHERE id=?",row.id());
                    }
                    after=rows.getLast().id();
                }
                return retractions.resourcePending(target) ? TerminalReapplyStore.Cleanup.PENDING : TerminalReapplyStore.Cleanup.COMPLETE;
            }
            if(!entry.targetKind().equals("STUDY_SERVER")) throw new IllegalArgumentException("Unknown terminal target");
            retractions.server(target);
            jdbc.update("DELETE FROM study_assistant_installs WHERE study_server_id=?",target);
            jdbc.update("UPDATE native_companion_requests SET evidence_json=NULL,outcome=CASE WHEN outcome IN ('ISSUED','ACCEPTING') THEN 'REJECTED' ELSE outcome END WHERE id IN (SELECT id FROM ai_generation_usage WHERE study_server_id=?)",target);
            boolean ready=jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_imports WHERE study_server_id=? AND revision=? AND event_id=? AND terminal_digest=? AND ready=TRUE",
                    Integer.class,target,entry.revision(),entry.eventId(),entry.digest())==2;
            if(recovery) ready=ready && historical.getObject().ready(entry,"COURSE") && historical.getObject().ready(entry,"CHANNEL");
            String scope=recovery ? "lifecycle_recovery_scope_ids" : "lifecycle_scope_import_ids";
            String scoped=ready ? " OR course_id IN (SELECT scope_id FROM "+scope+" WHERE study_server_id=? AND scope_kind='COURSE')"
                    +" OR resource_id IN (SELECT resource_id FROM resource_chunks WHERE course_id IN (SELECT scope_id FROM "+scope+" WHERE study_server_id=? AND scope_kind='COURSE'))" : "";
            UUID after=new UUID(0,0);
            while(true) {
                if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Agent terminal cleanup interrupted");
                Object[] args=ready ? new Object[]{after,target,target,target} : new Object[]{after,target};
                var ids=jdbc.query("SELECT resource_id FROM resource_index_lifecycle WHERE resource_id>? AND deleted=FALSE AND (study_server_id=?"+scoped+") ORDER BY resource_id LIMIT 256",
                        (rs,row)->rs.getObject(1,UUID.class),args);
                if(ids.isEmpty()) break;
                for(UUID resource:ids) chunks.deleteByResourceId(resource);
                after=ids.getLast();
            }
            if(ready) {
                retractions.serverChannels(target,scope);
                jdbc.update("UPDATE native_companion_requests SET evidence_json=NULL,outcome=CASE WHEN outcome IN ('ISSUED','ACCEPTING') THEN 'REJECTED' ELSE outcome END WHERE channel_id IN (SELECT scope_id FROM "+scope+" WHERE study_server_id=? AND scope_kind='CHANNEL')",target);
            }
            // Accounting metadata and downstream saved-answer reconciliation remain separate from payload erasure.
            return TerminalReapplyStore.Cleanup.PENDING;
        });
    }
    private record Evidence(UUID id,String json) {}
}
