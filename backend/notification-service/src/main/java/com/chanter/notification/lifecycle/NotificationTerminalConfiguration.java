package com.chanter.notification.lifecycle;

import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.SourceTerminalRecoveryController;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import({SourceTerminalRecoveryController.class,com.chanter.common.lifecycle.SourceDeletedScopeController.class})
public class NotificationTerminalConfiguration {
    @Bean com.chanter.common.lifecycle.RecoveryScopeStore notificationRecoveryScopeStore(JdbcTemplate jdbc,
            PlatformTransactionManager transactions,com.chanter.common.lifecycle.DeletedScopeStore current,TerminalReapplyStore terminal,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-restore-id:${CHANTER_RECOVERY_RESTORE_ID:}}") String restore) {
        if(!recovery && !restore.isBlank()) throw new IllegalArgumentException("Restore identity requires recovery mode");
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        java.util.UUID restoreId=recovery && !restore.isBlank() ? java.util.UUID.fromString(restore) : null;
        return new com.chanter.common.lifecycle.RecoveryScopeStore(jdbc,tx,current,restoreId,entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean com.chanter.common.lifecycle.DeletedScopeStore notificationDeletedScopeStore(JdbcTemplate jdbc,
            PlatformTransactionManager transactions,TerminalReapplyStore terminal) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new com.chanter.common.lifecycle.DeletedScopeStore(jdbc,tx,entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean com.chanter.common.lifecycle.AccountDeletionParticipant notificationDeletionParticipant(
            JdbcTemplate jdbc,PlatformTransactionManager transactions,com.chanter.common.events.DurableOutbox outbox,
            com.chanter.common.lifecycle.AccountDeletionProtocol protocol,TerminalReapplyStore terminal) {
        return new com.chanter.common.lifecycle.AccountDeletionParticipant("notification",
                new com.chanter.common.events.DurableConsumer(jdbc,new TransactionTemplate(transactions)),outbox,protocol,terminal,null);
    }
    @Bean TerminalReapplyStore notificationTerminalStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, ExportSnapshotStore snapshots,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            org.springframework.beans.factory.ObjectProvider<com.chanter.common.lifecycle.RecoveryScopeStore> historical) {
        var tx = new TransactionTemplate(transactions);
        tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"notification",entry -> {
            switch (entry.targetKind()) {
                case "ACCOUNT" -> {
                    snapshots.cancelAccount(entry.targetId());
                    jdbc.update("DELETE FROM notifications WHERE user_id=?",entry.targetId());
                    // Authored previews in other inboxes need owning source-ID reconciliation too.
                    return TerminalReapplyStore.Cleanup.PENDING;
                }
                case "STUDY_SERVER" -> {
                    jdbc.update("DELETE FROM notifications WHERE study_server_id=?",entry.targetId());
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_imports WHERE study_server_id=? AND revision=? AND event_id=? AND terminal_digest=? AND ready=TRUE",
                            Integer.class,entry.targetId(),entry.revision(),entry.eventId(),entry.digest())!=2)
                        return TerminalReapplyStore.Cleanup.PENDING;
                    if(recovery && (!historical.getObject().ready(entry,"COURSE") || !historical.getObject().ready(entry,"CHANNEL")))
                        return TerminalReapplyStore.Cleanup.PENDING;
                    jdbc.update("""
                            DELETE FROM notifications WHERE course_id IN (
                                SELECT scope_id FROM lifecycle_scope_import_ids WHERE study_server_id=? AND scope_kind='COURSE')
                            OR channel_id IN (SELECT scope_id FROM lifecycle_scope_import_ids WHERE study_server_id=? AND scope_kind='CHANNEL')
                            """.replace("lifecycle_scope_import_ids",recovery ? "lifecycle_recovery_scope_ids" : "lifecycle_scope_import_ids"),entry.targetId(),entry.targetId());
                }
                case "RESOURCE" -> jdbc.update("DELETE FROM notifications WHERE UPPER(source_type)='RESOURCE' AND source_id=?",entry.targetId());
                default -> throw new IllegalArgumentException("Unknown terminal target");
            }
            // Notifications are disposable derived delivery data. Moderation evidence is owned by auth.
            return TerminalReapplyStore.Cleanup.COMPLETE;
        });
    }
}
