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
@Import(SourceTerminalRecoveryController.class)
public class NotificationTerminalConfiguration {
    @Bean com.chanter.common.lifecycle.AccountDeletionParticipant notificationDeletionParticipant(
            JdbcTemplate jdbc,PlatformTransactionManager transactions,com.chanter.common.events.DurableOutbox outbox,
            com.chanter.common.lifecycle.AccountDeletionProtocol protocol,TerminalReapplyStore terminal) {
        return new com.chanter.common.lifecycle.AccountDeletionParticipant("notification",
                new com.chanter.common.events.DurableConsumer(jdbc,new TransactionTemplate(transactions)),outbox,protocol,terminal,null);
    }
    @Bean TerminalReapplyStore notificationTerminalStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, ExportSnapshotStore snapshots) {
        var tx = new TransactionTemplate(transactions);
        tx.setTimeout(30);
        return new TerminalReapplyStore(jdbc,tx,"notification",entry -> {
            switch (entry.targetKind()) {
                case "ACCOUNT" -> {
                    snapshots.cancelAccount(entry.targetId());
                    jdbc.update("DELETE FROM notifications WHERE user_id=?",entry.targetId());
                }
                case "STUDY_SERVER" -> jdbc.update("DELETE FROM notifications WHERE study_server_id=?",entry.targetId());
                case "RESOURCE" -> jdbc.update("DELETE FROM notifications WHERE UPPER(source_type)='RESOURCE' AND source_id=?",entry.targetId());
                default -> throw new IllegalArgumentException("Unknown terminal target");
            }
            // Notifications are disposable derived delivery data. Moderation evidence is owned by auth.
            return TerminalReapplyStore.Cleanup.COMPLETE;
        });
    }
}
