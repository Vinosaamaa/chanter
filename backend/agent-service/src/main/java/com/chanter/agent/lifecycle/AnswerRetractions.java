package com.chanter.agent.lifecycle;

import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Payload erasure and exact downstream retraction commit together. ID-only receipts outlive the erased answer. */
@Component
public final class AnswerRetractions {
    private final JdbcTemplate jdbc;
    private final DurableOutbox outbox;
    private final ObjectMapper mapper;
    private final org.springframework.beans.factory.ObjectProvider<com.chanter.common.lifecycle.TerminalReapplyStore> terminal;
    private final org.springframework.beans.factory.ObjectProvider<ResourceDeletionReconciliation> deletions;
    public AnswerRetractions(JdbcTemplate jdbc,DurableOutbox outbox,ObjectMapper mapper,
            org.springframework.beans.factory.ObjectProvider<com.chanter.common.lifecycle.TerminalReapplyStore> terminal,
            org.springframework.beans.factory.ObjectProvider<ResourceDeletionReconciliation> deletions) {
        this.jdbc=jdbc; this.outbox=outbox; this.mapper=mapper; this.terminal=terminal;this.deletions=deletions;
    }
    public void resource(UUID id) { erase("a.id IN (SELECT answer_id FROM study_assistant_answer_sources WHERE resource_id=?)",id); }
    public void account(UUID id) { erase("a.learner_user_id=?",id); }
    public void server(UUID id) { erase("a.study_server_id=?",id); }
    public void serverChannels(UUID id,String table) {
        if(!java.util.Set.of("lifecycle_scope_import_ids","lifecycle_recovery_scope_ids").contains(table)) throw new IllegalArgumentException("Invalid scope table");
        erase("a.channel_id IN (SELECT scope_id FROM "+table+" WHERE study_server_id=? AND scope_kind='CHANNEL')",id);
    }
    private void erase(String predicate,UUID target) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Answer erasure requires source transaction");
        while(true) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Answer retraction interrupted");
            var rows=jdbc.query("SELECT a.id,a.support_question_id,a.channel_id,a.learner_user_id,a.study_server_id FROM study_assistant_answers a WHERE "+predicate+" ORDER BY a.id LIMIT 256",
                    (rs,n) -> new Saved(new AnswerRetraction(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class)),rs.getObject(5,UUID.class)),target);
            if(rows.isEmpty()) return;
            for(var row:rows) {
                var answer=row.answer();
                UUID event;
                try { event=outbox.append("message",AnswerRetraction.KIND,answer.aggregateKey(),mapper.writeValueAsString(answer)); }
                catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid answer retraction",invalid); }
                jdbc.update("INSERT INTO lifecycle_answer_retractions(answer_id,question_id,channel_id,author_id,study_server_id,event_id,receipt_state) VALUES (?,?,?,?,?,?,'PENDING')",
                        answer.answerId(),answer.questionId(),answer.channelId(),answer.authorId(),row.server(),event);
                jdbc.update("INSERT INTO lifecycle_answer_retraction_resources(answer_id,resource_id) SELECT DISTINCT answer_id,resource_id FROM study_assistant_answer_sources WHERE answer_id=?",answer.answerId());
                jdbc.update("DELETE FROM study_assistant_answers WHERE id=?",answer.answerId());
            }
        }
    }
    public boolean resourcePending(UUID target) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_answer_retractions a JOIN lifecycle_answer_retraction_resources r ON r.answer_id=a.answer_id WHERE r.resource_id=? AND a.receipt_state<>'COMPLETE'",
                Integer.class,target)>0;
    }
    public boolean accountPending(UUID account) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_answer_retractions WHERE author_id=? AND receipt_state<>'COMPLETE'",Long.class,account)>0;
    }
    public void acknowledge(AnswerReconciliation receipt) {
        jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        var answer=receipt.answer();
        var rows=jdbc.query("SELECT receipt_state FROM lifecycle_answer_retractions WHERE answer_id=? AND question_id=? AND channel_id=? AND author_id=?",
                (rs,n)->rs.getString(1),answer.answerId(),answer.questionId(),answer.channelId(),answer.authorId());
        if(rows.size()!=1) throw new IllegalArgumentException("Unknown answer reconciliation");
        if("COMPLETE".equals(rows.getFirst()) && !"COMPLETE".equals(receipt.state())) throw new IllegalArgumentException("Answer reconciliation regressed");
        jdbc.update("UPDATE lifecycle_answer_retractions SET receipt_state=? WHERE answer_id=?",receipt.state(),answer.answerId());
        for(UUID resource:jdbc.query("SELECT resource_id FROM lifecycle_answer_retraction_resources WHERE answer_id=? ORDER BY resource_id",
                (rs,n)->rs.getObject(1,UUID.class),answer.answerId())) {
            terminal.getObject().reconcile("RESOURCE",resource);
            deletions.getObject().completeResource(resource);
        }
        terminal.getObject().reconcile("ACCOUNT",answer.authorId());
    }
    private record Saved(AnswerRetraction answer,UUID server) { }
}
