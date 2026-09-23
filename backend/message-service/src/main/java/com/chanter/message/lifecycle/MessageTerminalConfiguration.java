package com.chanter.message.lifecycle;

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
public class MessageTerminalConfiguration {
    @Bean DeletedScopeStore messageDeletedScopeStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,TerminalReapplyStore terminal) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new DeletedScopeStore(jdbc,tx,entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean RecoveryScopeStore messageRecoveryScopeStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            DeletedScopeStore current,TerminalReapplyStore terminal,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-restore-id:${CHANTER_RECOVERY_RESTORE_ID:}}") String restore) {
        if(!recovery && !restore.isBlank()) throw new IllegalArgumentException("Restore identity requires recovery mode");
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new RecoveryScopeStore(jdbc,tx,current,recovery && !restore.isBlank() ? UUID.fromString(restore) : null,
                entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean AccountDeletionParticipant messageDeletionParticipant(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            com.chanter.common.events.DurableOutbox outbox,AccountDeletionProtocol protocol,TerminalReapplyStore terminal) {
        return new AccountDeletionParticipant("message",new com.chanter.common.events.DurableConsumer(jdbc,new TransactionTemplate(transactions)),
                outbox,protocol,terminal,null);
    }
    @Bean TerminalReapplyStore messageTerminalStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            ExportSnapshotStore snapshots,ErasedContentDelivery content,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            org.springframework.beans.factory.ObjectProvider<RecoveryScopeStore> historical) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"message",entry -> {
            if(entry.targetKind().equals("RESOURCE")) return TerminalReapplyStore.Cleanup.COMPLETE;
            snapshots.invalidateRetained();
            String questions;
            String messages;
            String faqs;
            if(entry.targetKind().equals("ACCOUNT")) {
                snapshots.cancelAccount(entry.targetId());
                questions="SELECT id FROM support_questions WHERE sender_user_id=?";
                messages="SELECT id FROM channel_messages WHERE sender_user_id=?";
                faqs="SELECT id FROM approved_faqs WHERE approved_by_user_id=?";
                retain(jdbc,entry,"QUESTION_PREVIEW","SELECT support_question_id FROM support_question_replies WHERE author_user_id=?");
                jdbc.update("DELETE FROM support_question_replies WHERE author_user_id=?",entry.targetId());
                jdbc.update("DELETE FROM direct_messages WHERE sender_user_id=? OR recipient_user_id=?",entry.targetId(),entry.targetId());
                jdbc.update("DELETE FROM friend_requests WHERE sender_user_id=? OR recipient_user_id=?",entry.targetId(),entry.targetId());
                jdbc.update("DELETE FROM user_blocks WHERE blocker_user_id=? OR blocked_user_id=?",entry.targetId(),entry.targetId());
                jdbc.update("DELETE FROM social_pair_locks WHERE first_user_id=? OR second_user_id=?",entry.targetId(),entry.targetId());
                jdbc.update("DELETE FROM ta_queue_items WHERE learner_user_id=?",entry.targetId());
                jdbc.update("UPDATE ta_queue_items SET assigned_ta_user_id=NULL,status=CASE WHEN status='PICKED_UP' THEN 'OPEN' ELSE status END WHERE assigned_ta_user_id=?",entry.targetId());
            } else {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_imports WHERE study_server_id=? AND revision=? AND event_id=? AND terminal_digest=? AND ready=TRUE",
                        Integer.class,entry.targetId(),entry.revision(),entry.eventId(),entry.digest())!=2)
                    return TerminalReapplyStore.Cleanup.PENDING;
                if(recovery && (!historical.getObject().ready(entry,"COURSE") || !historical.getObject().ready(entry,"CHANNEL")))
                    return TerminalReapplyStore.Cleanup.PENDING;
                String table=recovery ? "lifecycle_recovery_scope_ids" : "lifecycle_scope_import_ids";
                String channels="SELECT scope_id FROM "+table+" WHERE study_server_id=? AND scope_kind='CHANNEL'";
                questions="SELECT id FROM support_questions WHERE channel_id IN ("+channels+")";
                messages="SELECT id FROM channel_messages WHERE channel_id IN ("+channels+")";
                faqs="SELECT id FROM approved_faqs WHERE course_id IN (SELECT scope_id FROM "+table+" WHERE study_server_id=? AND scope_kind='COURSE')";
                jdbc.update("DELETE FROM ta_queue_items WHERE channel_id IN ("+channels+")",entry.targetId());
            }
            retain(jdbc,entry,"MESSAGE",messages);
            retain(jdbc,entry,"QUESTION",questions);
            retain(jdbc,entry,"QUESTION_PREVIEW",questions);
            retain(jdbc,entry,"FAQ",faqs);
            retain(jdbc,entry,"FAQ","SELECT approved_faq_id FROM approved_faq_source_questions WHERE support_question_id IN ("+questions+")");
            // Keep only immutable IDs required to erase downstream copies and fence late delivery.
            String retained="SELECT source_id FROM lifecycle_erased_content WHERE target_kind=? AND target_id=? AND source_kind=?";
            jdbc.update("DELETE FROM approved_faqs WHERE id IN ("+retained+")",entry.targetKind(),entry.targetId(),"FAQ");
            jdbc.update("DELETE FROM ta_queue_items WHERE support_question_id IN ("+retained+")",entry.targetKind(),entry.targetId(),"QUESTION");
            jdbc.update("DELETE FROM support_questions WHERE id IN ("+retained+")",entry.targetKind(),entry.targetId(),"QUESTION");
            jdbc.update("DELETE FROM channel_messages WHERE id IN ("+retained+")",entry.targetKind(),entry.targetId(),"MESSAGE");
            if(entry.targetKind().equals("ACCOUNT")) content.start(entry);
            // Existing durable payload copies and downstream deletion receipts still require reconciliation.
            return TerminalReapplyStore.Cleanup.PENDING;
        });
    }
    private static void retain(JdbcTemplate jdbc,TerminalJournal.Entry entry,String kind,String source) {
        jdbc.update("""
                INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id)
                SELECT ?,?,?,?,?,?,q.id FROM (SELECT DISTINCT candidate.* FROM (%s) candidate) q(id)
                WHERE NOT EXISTS(SELECT 1 FROM lifecycle_erased_content old WHERE old.target_kind=? AND old.target_id=? AND old.source_kind=? AND old.source_id=q.id)
                """.formatted(source),entry.targetKind(),entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,
                entry.targetId(),entry.targetKind(),entry.targetId(),kind);
    }
}
