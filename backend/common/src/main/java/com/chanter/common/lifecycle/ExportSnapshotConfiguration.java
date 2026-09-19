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
    @Bean ExportSnapshotStore exportSnapshotStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, ObjectMapper mapper,
            @Value("${spring.application.name}") String serviceName) {
        return new ExportSnapshotStore(jdbc, new TransactionTemplate(transactions), mapper, Clock.systemUTC(), serviceName.replace("-service", ""));
    }
    @Bean AccountExportProtocol accountExportProtocol(ObjectMapper mapper) { return new AccountExportProtocol(mapper); }
    @Bean ExportExpiry exportExpiry(ExportSnapshotStore snapshots) { return new ExportExpiry(snapshots); }
    static final class ExportExpiry {
        private final ExportSnapshotStore snapshots;
        ExportExpiry(ExportSnapshotStore snapshots) { this.snapshots = snapshots; }
        @Scheduled(fixedDelayString="${chanter.lifecycle.export-expiry-poll-ms:300000}")
        public void expire() { snapshots.expire(); }
    }
}
