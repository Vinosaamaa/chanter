package com.chanter.search.lifecycle;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableOutbox;
import com.chanter.common.lifecycle.AccountDeletionParticipant;
import com.chanter.common.lifecycle.AccountDeletionProtocol;
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
@Import(SourceTerminalRecoveryController.class)
public class SearchTerminalConfiguration {
    @Bean AccountDeletionParticipant searchDeletionParticipant(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            DurableOutbox outbox, AccountDeletionProtocol protocol, TerminalReapplyStore terminal) {
        return new AccountDeletionParticipant("search",new DurableConsumer(jdbc,new TransactionTemplate(transactions)),
                outbox,protocol,terminal,null);
    }

    @Bean TerminalReapplyStore searchTerminalStore(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            ExportSnapshotStore snapshots) {
        var tx=new TransactionTemplate(transactions);
        tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"search",entry -> {
            switch(entry.targetKind()) {
                case "ACCOUNT" -> snapshots.cancelAccount(entry.targetId());
                case "STUDY_SERVER" -> jdbc.update("DELETE FROM search_index_entries WHERE study_server_id=?",entry.targetId());
                case "RESOURCE" -> {
                    jdbc.update("DELETE FROM search_index_entries WHERE document_type='RESOURCE' AND source_id=?",entry.targetId());
                    return TerminalReapplyStore.Cleanup.COMPLETE;
                }
                default -> throw new IllegalArgumentException("Unknown terminal target");
            }
            // Author identity and legacy course-to-server ownership belong to the canonical sources.
            return TerminalReapplyStore.Cleanup.PENDING;
        });
    }
}
