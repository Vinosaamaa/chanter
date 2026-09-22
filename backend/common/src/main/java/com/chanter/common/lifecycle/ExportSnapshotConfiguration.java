package com.chanter.common.lifecycle;

import com.chanter.common.events.OutboxConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import({OutboxConfiguration.class, ExportSnapshotController.class})
public class ExportSnapshotConfiguration {
    @Bean(destroyMethod="close") ExportSourceExecution exportSourceExecution(PlatformTransactionManager transactions) {
        return new ExportSourceExecution(transactions);
    }
    @Bean ExportSnapshotStore exportSnapshotStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, ObjectMapper mapper,
            @Value("${spring.application.name}") String serviceName) {
        var tx = new TransactionTemplate(transactions);
        tx.setTimeout((int) ExportSourceExecution.CAPTURE_LIMIT.toSeconds());
        return new ExportSnapshotStore(jdbc, tx, mapper, Clock.systemUTC(), serviceName.replace("-service", ""));
    }
    @Bean AccountExportProtocol accountExportProtocol(ObjectMapper mapper) { return new AccountExportProtocol(mapper); }
    @Bean AccountDeletionProtocol accountDeletionProtocol(ObjectMapper mapper) { return new AccountDeletionProtocol(mapper); }
    @Bean DeletedScopeDelivery deletedScopeDelivery(JdbcTemplate jdbc,PlatformTransactionManager transactions,ObjectMapper mapper,
            com.chanter.common.events.DurableOutbox outbox,@Value("${spring.application.name}") String serviceName,
            org.springframework.beans.factory.ObjectProvider<DeletedScopeDelivery.Source> source,
            org.springframework.beans.factory.ObjectProvider<DeletedScopeStore> imports,
            org.springframework.beans.factory.ObjectProvider<TerminalReapplyStore> terminal,
            @Value("${chanter.recovery-mode:false}") boolean recovery) {
        var tx=new TransactionTemplate(transactions); tx.setTimeout(30);
        return new DeletedScopeDelivery(serviceName.replace("-service",""),jdbc,tx,outbox,mapper,source,imports,terminal,recovery);
    }
    @Bean @com.chanter.common.recovery.OrdinaryOperation
    ExportExpiry exportExpiry(ExportSnapshotStore snapshots) { return new ExportExpiry(snapshots); }
    static final class ExportExpiry {
        private final ExportSnapshotStore snapshots;
        ExportExpiry(ExportSnapshotStore snapshots) { this.snapshots = snapshots; }
        @Scheduled(fixedDelayString="${chanter.lifecycle.export-expiry-poll-ms:300000}")
        public void expire() { snapshots.expire(); }
    }
}
