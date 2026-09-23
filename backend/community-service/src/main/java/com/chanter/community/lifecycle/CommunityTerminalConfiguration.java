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
            com.chanter.common.lifecycle.DeletedScopeDelivery delivery,CommunityAccountCleanup accounts) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"community",entry -> {
            switch(entry.targetKind()) {
                case "ACCOUNT" -> {
                    ownership.close(entry.targetId());
                    snapshots.cancelAccount(entry.targetId());
                    snapshots.invalidateRetained();
                    accounts.erase(entry);
                    // Restored pre-transfer ownership, durable payloads and downstream copies still need disposition.
                    return TerminalReapplyStore.Cleanup.PENDING;
                }
                case "STUDY_SERVER" -> {
                    if(!scopes.capture(entry)) return TerminalReapplyStore.Cleanup.PENDING;
                    delivery.start(entry);
                    // Remove events first: course/cohort SET NULL actions cannot satisfy their visibility constraint.
                    jdbc.update("DELETE FROM community_events WHERE study_server_id=?",entry.targetId());
                    jdbc.update("DELETE FROM study_servers WHERE id=?",entry.targetId());
                    // Outbox payload and already captured export retention are tracked separately from graph erasure.
                    return TerminalReapplyStore.Cleanup.PENDING;
                }
                case "RESOURCE" -> { return TerminalReapplyStore.Cleanup.COMPLETE; }
                default -> throw new IllegalArgumentException("Unknown terminal target");
            }
        });
    }
}
