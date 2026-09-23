package com.chanter.common.lifecycle;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableOutbox;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import({ExportSnapshotConfiguration.class, ExportRequestController.class})
public class ExportSourceConfiguration {
    @Bean ExportParticipant exportParticipant(@Value("${spring.application.name}") String serviceName,
            ExportSnapshotStore snapshots, AccountExportProjection projection, JdbcTemplate jdbc, PlatformTransactionManager transactions,
            DurableOutbox outbox, AccountExportProtocol protocol) {
        return new ExportParticipant(serviceName.replace("-service", ""), snapshots, projection,
                new DurableConsumer(jdbc, new TransactionTemplate(transactions)), outbox, protocol, Clock.systemUTC());
    }
}
