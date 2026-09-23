package com.chanter.media.lifecycle;

import com.chanter.common.lifecycle.*;
import com.chanter.media.application.ResourceLifecycle;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import({SourceTerminalRecoveryController.class,SourceDeletedScopeController.class})
public class MediaTerminalConfiguration {
    @Bean SourceDeletionRequests mediaSourceDeletionRequests(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            com.chanter.common.events.DurableOutbox outbox,AccountDeletionProtocol protocol) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new SourceDeletionRequests("RESOURCE",jdbc,tx,outbox,protocol);
    }
    @Bean DeletedScopeStore mediaDeletedScopeStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,TerminalReapplyStore terminal) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new DeletedScopeStore(jdbc,tx,entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean RecoveryScopeStore mediaRecoveryScopeStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            DeletedScopeStore current,TerminalReapplyStore terminal,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-restore-id:${CHANTER_RECOVERY_RESTORE_ID:}}") String restore) {
        if(!recovery && !restore.isBlank()) throw new IllegalArgumentException("Restore identity requires recovery mode");
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new RecoveryScopeStore(jdbc,tx,current,recovery && !restore.isBlank() ? UUID.fromString(restore) : null,
                entry -> terminal.cleanup(entry),terminal::reconcile);
    }
    @Bean AccountDeletionParticipant mediaDeletionParticipant(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            com.chanter.common.events.DurableOutbox outbox,AccountDeletionProtocol protocol,TerminalReapplyStore terminal) {
        return new AccountDeletionParticipant("media",new com.chanter.common.events.DurableConsumer(jdbc,new TransactionTemplate(transactions)),
                outbox,protocol,terminal,null);
    }
    @Bean TerminalReapplyStore mediaTerminalStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            ExportSnapshotStore snapshots,ResourceLifecycle resources,ErasedContentDelivery content,
            @org.springframework.beans.factory.annotation.Value("${chanter.recovery-mode:false}") boolean recovery,
            org.springframework.beans.factory.ObjectProvider<RecoveryScopeStore> historical) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"media",entry -> {
            if(entry.targetKind().equals("ACCOUNT")) {
                snapshots.cancelAccount(entry.targetId());
                jdbc.update("""
                    INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id)
                    SELECT 'ACCOUNT',?,?,?,?,'RESOURCE',r.id FROM course_resources r WHERE r.uploaded_by_user_id=?
                      AND NOT EXISTS(SELECT 1 FROM lifecycle_erased_content old WHERE old.target_kind='ACCOUNT' AND old.target_id=? AND old.source_kind='RESOURCE' AND old.source_id=r.id)
                    """,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),entry.targetId(),entry.targetId());
                content.start(entry);
            }
            // Files may appear in any currently retained authorized source export.
            snapshots.invalidateRetained();
            boolean ready=false;
            if(entry.targetKind().equals("STUDY_SERVER")) {
                ready=jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_scope_imports WHERE study_server_id=? AND revision=? AND event_id=? AND terminal_digest=? AND ready=TRUE",
                        Integer.class,entry.targetId(),entry.revision(),entry.eventId(),entry.digest())==2;
                if(recovery) ready=ready && historical.getObject().ready(entry,"COURSE") && historical.getObject().ready(entry,"CHANNEL");
            }
            resources.queueTerminalDeletion(entry,ready,recovery ? "lifecycle_recovery_scope_ids" : "lifecycle_scope_import_ids");
            // No provider calls occur here. A restored empty scratch directory is never object-erasure evidence.
            // Physical object deletion, downstream receipts and metadata disposition remain required.
            return TerminalReapplyStore.Cleanup.PENDING;
        });
    }
}
