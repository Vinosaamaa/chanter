package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class EmailQueueMetricsTest {
    @Test void pendingDeliveryAndExpiryFollowCommittedOutboxStateWithoutContentLabels() {
        String nativeUrl = System.getProperty("chanter.telemetry.jdbc");
        var source = nativeUrl == null
                ? new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                : new DriverManagerDataSource(nativeUrl, "metric_test", "metric_test");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V4__transactional_email_outbox.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var transactions = new DataSourceTransactionManager(source);
        // The database stores microseconds; a nanosecond fixed clock can precede
        // the rounded next_attempt_at and make otherwise-due delivery disappear.
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var registry = new SimpleMeterRegistry();
        try (var monitor = new EmailQueueMetrics().emailQueueMetrics(source, registry)) {
            var transport = mock(SmtpEmailTransport.class);
            var first = new JdbcEmailOutbox(jdbc, transactions, transport, registry, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC));
            first.send("private-canary@example.test", "private-canary", "private-canary");
            monitor.sample();
            assertThat(registry.get("chanter.email.pending").gauge().value()).isEqualTo(1);
            assertThat(first.deliverNext()).isTrue();
            monitor.sample();
            assertThat(registry.get("chanter.email.pending").gauge().value()).isZero();
            assertThat(registry.get("chanter.email.failed").gauge().value()).isZero();
            first.send("private-canary@example.test", "private-canary", "private-canary");
            var later = new JdbcEmailOutbox(jdbc, transactions, transport, registry, Duration.ofHours(1), Clock.fixed(now.plusSeconds(7200), ZoneOffset.UTC));
            later.deliverNext();
            monitor.sample();
            assertThat(registry.get("chanter.email.pending").gauge().value()).isZero();
            assertThat(registry.get("chanter.email.failed").gauge().value()).isEqualTo(1);
            assertThat(registry.get("chanter.email.collection.healthy").gauge().value()).isEqualTo(1);
            assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags().toString()).doesNotContain("private-canary"));
            jdbc.execute("DROP TABLE auth_email_outbox");
            monitor.sample();
            assertThat(registry.get("chanter.email.collection.healthy").gauge().value()).isZero();
        } finally { registry.close(); }
    }
}
