package com.chanter.community.lifecycle;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableOutbox;
import com.chanter.common.lifecycle.AccountDeletionParticipant;
import com.chanter.common.lifecycle.AccountDeletionProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.common.lifecycle.SourceTerminalRecoveryController;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import(SourceTerminalRecoveryController.class)
public class CommunityTerminalConfiguration {
    @Bean com.chanter.common.lifecycle.DeletedScopeDelivery.Source communityScopeDeliverySource(DeletedStudyServerScope scopes) {
        return (entry,kind,after) -> scopes.page(entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,after,256);
    }
    @Bean com.chanter.common.lifecycle.SourceDeletionRequests communitySourceDeletionRequests(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            DurableOutbox outbox,AccountDeletionProtocol protocol) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new com.chanter.common.lifecycle.SourceDeletionRequests("STUDY_SERVER",jdbc,tx,outbox,protocol);
    }
    @Bean AccountDeletionParticipant communityDeletionParticipant(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            DurableOutbox outbox,AccountDeletionProtocol protocol,TerminalReapplyStore terminal,CommunityOwnershipFence ownership) {
        var preparation=new AccountDeletionParticipant.OwnershipPreparation() {
            public String prepare(UUID account,UUID job) { return ownership.prepare(account,job).name(); }
            public void release(UUID account,UUID job) { ownership.release(account,job); }
        };
        return new AccountDeletionParticipant("community",new DurableConsumer(jdbc,new TransactionTemplate(transactions)),
                outbox,protocol,terminal,preparation);
    }

    @Bean TerminalReapplyStore communityTerminalStore(JdbcTemplate jdbc,PlatformTransactionManager transactions,
            ExportSnapshotStore snapshots,CommunityOwnershipFence ownership,DeletedStudyServerScope scopes,
            com.chanter.common.lifecycle.DeletedScopeDelivery delivery,CommunityAccountCleanup accounts,
            com.chanter.common.lifecycle.ErasedContentDelivery content,com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        var payloads=new com.chanter.common.lifecycle.ServerPayloadCleanup(jdbc,mapper);
        return new TerminalReapplyStore(jdbc,tx,"community",entry -> {
            switch(entry.targetKind()) {
                case "ACCOUNT" -> {
                    ownership.close(entry.targetId());
                    snapshots.cancelAccount(entry.targetId());
                    snapshots.invalidateRetained();
                    accounts.erase(entry);
                    content.start(entry);
                    return content.complete(entry) ? accounts.disposition(entry.targetId()) : TerminalReapplyStore.Cleanup.PENDING;
                }
                case "STUDY_SERVER" -> {
                    if(!scopes.capture(entry)) return TerminalReapplyStore.Cleanup.PENDING;
                    delivery.start(entry);
                    snapshots.invalidateRetained();
                    retainServer(jdbc,entry,"ANNOUNCEMENT","SELECT id FROM community_announcements WHERE study_server_id=?");
                    retainServer(jdbc,entry,"EVENT","SELECT id FROM community_events WHERE study_server_id=?");
                    retainServer(jdbc,entry,"OFFICE_HOURS","SELECT o.id FROM office_hours_sessions o JOIN cohorts h ON h.id=o.cohort_id JOIN courses c ON c.id=h.course_id WHERE c.study_server_id=?");
                    payloads.erase(entry,scopes.payloadScopeTable(entry));
                    // Remove events first: course/cohort SET NULL actions cannot satisfy their visibility constraint.
                    jdbc.update("DELETE FROM community_events WHERE study_server_id=?",entry.targetId());
                    jdbc.update("DELETE FROM office_hours_sessions WHERE id IN (SELECT source_id FROM lifecycle_erased_content WHERE target_kind='STUDY_SERVER' AND target_id=? AND source_kind='OFFICE_HOURS')",entry.targetId());
                    jdbc.update("DELETE FROM study_servers WHERE id=?",entry.targetId());
                    return delivery.complete(entry) ? TerminalReapplyStore.Cleanup.COMPLETE : TerminalReapplyStore.Cleanup.PENDING;
                }
                case "RESOURCE" -> { return TerminalReapplyStore.Cleanup.COMPLETE; }
                default -> throw new IllegalArgumentException("Unknown terminal target");
            }
        });
    }
    private static void retainServer(JdbcTemplate jdbc,com.chanter.common.lifecycle.TerminalJournal.Entry entry,String kind,String select) {
        jdbc.update("""
            INSERT INTO lifecycle_erased_content(target_kind,target_id,revision,event_id,terminal_digest,source_kind,source_id)
            SELECT 'STUDY_SERVER',?,?,?,?,?,q.id FROM (%s) q
            WHERE NOT EXISTS(SELECT 1 FROM lifecycle_erased_content c WHERE c.target_kind='STUDY_SERVER' AND c.target_id=? AND c.source_kind=? AND c.source_id=q.id)
            """.formatted(select),entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,entry.targetId(),entry.targetId(),kind);
    }
}
