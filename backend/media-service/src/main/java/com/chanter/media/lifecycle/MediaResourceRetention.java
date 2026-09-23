package com.chanter.media.lifecycle;

import com.chanter.common.lifecycle.*;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Disposes of source payload only after physical closure and exact downstream reconciliation. */
@Component
public final class MediaResourceRetention {
    private final JdbcTemplate jdbc;
    private final ErasedContentDelivery content;
    public MediaResourceRetention(JdbcTemplate jdbc,ErasedContentDelivery content) {this.jdbc=jdbc;this.content=content;}
    public TerminalReapplyStore.Cleanup disposition(TerminalJournal.Entry entry,boolean scoped,String scopeTable) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Media disposition requires source transaction");
        entry.validate();jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        if(!Set.of("lifecycle_scope_import_ids","lifecycle_recovery_scope_ids").contains(scopeTable)) throw new IllegalArgumentException("Invalid scope table");
        if(entry.targetKind().equals("STUDY_SERVER") && !scoped || entry.targetKind().equals("ACCOUNT") && !content.complete(entry))
            return TerminalReapplyStore.Cleanup.PENDING;
        String match=switch(entry.targetKind()) {
            case "ACCOUNT" -> "(r.uploaded_by_user_id=? OR r.id IN (SELECT source_id FROM lifecycle_erased_content WHERE target_kind='ACCOUNT' AND target_id=? AND source_kind='RESOURCE'))";
            case "RESOURCE" -> "r.id=?";
            case "STUDY_SERVER" -> "(r.study_server_id=? OR r.course_id IN (SELECT scope_id FROM "+scopeTable+" WHERE study_server_id=? AND scope_kind='COURSE'))";
            default -> throw new IllegalArgumentException("Unknown terminal target");
        };
        Object[] args=entry.targetKind().equals("RESOURCE") ? new Object[]{entry.targetId()} : new Object[]{entry.targetId(),entry.targetId()};
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM course_resources r WHERE "+match,Long.class,args);
        if(count==0) return entry.targetKind().equals("RESOURCE") ? TerminalReapplyStore.Cleanup.PENDING : TerminalReapplyStore.Cleanup.COMPLETE;
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM course_resources r WHERE "+match+" AND (state<>'DELETED' OR storage_write_settled=FALSE OR lease_id IS NOT NULL OR byte_reservation=TRUE OR deletion_event_id IS NULL OR deletion_reconciled=FALSE))",Boolean.class,args)))
            return TerminalReapplyStore.Cleanup.PENDING;
        // Claimed copies are defeated by the recipients' permanent resource/content fences.
        jdbc.update("""
            UPDATE durable_outbox SET payload='{}',status='ERASED',lease_token=NULL,lease_until=NULL,last_error=NULL
            WHERE ((destination='search' AND kind='RESOURCE') OR (destination='agent' AND kind='RESOURCE_CHANGED'))
              AND aggregate_key IN (SELECT 'RESOURCE:' || CAST(r.id AS VARCHAR) FROM course_resources r WHERE %s)
            """.formatted(match),args);
        jdbc.update("""
            UPDATE course_resources SET title='Deleted resource',file_name='deleted',content_type='application/octet-stream',
              uploaded_by_user_id=NULL,idempotency_key=NULL,ai_approved=FALSE,ingestion_status='NONE',ingestion_signals='[]',ingestion_event_id=NULL
            WHERE id IN (SELECT r.id FROM course_resources r WHERE %s)
            """.formatted(match),args);
        return TerminalReapplyStore.Cleanup.PRESERVED;
    }
}
