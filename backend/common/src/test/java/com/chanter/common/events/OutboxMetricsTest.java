package com.chanter.common.events;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxMetricsTest {
    @Test void claimedWorkRemainsPendingAndDeliveredWorkLeavesTheBacklog() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source); jdbc.execute(DurableOutbox.SCHEMA);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var outbox = new DurableOutbox(jdbc, transactions, "message", Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC));
        transactions.executeWithoutResult(ignored -> outbox.append("notification", "NOTIFICATION_REQUESTED", "item:fixture", "{}"));
        var registry = new SimpleMeterRegistry();
        try (var monitor = new OutboxMetrics().outboxQueueMetrics(source, registry)) {
            monitor.sample();
            assertThat(registry.get("chanter.events.pending").gauge().value()).isEqualTo(1);
            var delivery = outbox.claim().orElseThrow();
            monitor.sample();
            assertThat(registry.get("chanter.events.pending").gauge().value()).isEqualTo(1);
            outbox.delivered(delivery);
            monitor.sample();
            assertThat(registry.get("chanter.events.pending").gauge().value()).isZero();
            assertThat(registry.get("chanter.events.oldest.age").gauge().value()).isZero();
            assertThat(registry.get("chanter.events.collection.healthy").gauge().value()).isEqualTo(1);
        } finally { registry.close(); }
    }
}
