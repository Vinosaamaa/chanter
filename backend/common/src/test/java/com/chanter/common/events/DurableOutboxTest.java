package com.chanter.common.events;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DurableOutboxTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DurableOutbox outbox;
    Instant now = Instant.parse("2026-09-19T00:00:00Z");

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(DurableOutbox.SCHEMA);
        jdbc.execute("CREATE TABLE source_rows (id INT PRIMARY KEY)");
        outbox = new DurableOutbox(jdbc, tx, "community", Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void sourceAndEventRollbackTogetherAndRequireATransaction() {
        assertThatThrownBy(() -> outbox.append("search", "EVENT", "event:1", "{}"))
                .isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO source_rows VALUES (1)");
            outbox.append("search", "EVENT", "event:1", "{}");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_rows", Integer.class)).isZero();
        assertThat(outbox.claim()).isEmpty();
    }

    @Test void failedDeliverySurvivesRestartThenAcknowledgesOnce() {
        tx.executeWithoutResult(status -> outbox.append("notification", "NOTIFICATION", "recipient:1", "{}"));
        var first = outbox.claim().orElseThrow();
        outbox.failed(first, "HTTP_503");
        assertThat(outbox.claim()).isEmpty();
        outbox = new DurableOutbox(jdbc, tx, "community", Clock.fixed(now.plusSeconds(301), ZoneOffset.UTC));
        var retry = outbox.claim().orElseThrow();
        assertThat(retry.event().id()).isEqualTo(first.event().id());
        outbox.delivered(retry);
        assertThat(outbox.claim()).isEmpty();
        assertThat(outbox.stats().delivered()).isEqualTo(1);
    }

    @Test void expiredLeaseCanRetryButOldWorkerCannotAcknowledgeIt() {
        tx.executeWithoutResult(status -> outbox.append("search", "EVENT", "event:1", "{}"));
        var first = outbox.claim().orElseThrow();
        outbox = new DurableOutbox(jdbc, tx, "community", Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC));
        var retry = outbox.claim().orElseThrow();
        outbox.delivered(first);
        assertThat(outbox.stats().delivered()).isZero();
        outbox.delivered(retry);
        assertThat(outbox.stats().delivered()).isEqualTo(1);
    }

    @Test void retriesAreBoundedAndReplayPreservesIdentity() {
        tx.executeWithoutResult(status -> outbox.append("search", "EVENT", "event:1", "{}"));
        UUID id = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            outbox = new DurableOutbox(jdbc, tx, "community", Clock.fixed(now.plusSeconds(attempt * 301L), ZoneOffset.UTC));
            var delivery = outbox.claim().orElseThrow();
            id = delivery.event().id();
            outbox.failed(delivery, "HTTP_503");
        }
        assertThat(outbox.stats().failed()).isEqualTo(1);
        assertThat(outbox.claim()).isEmpty();
        assertThat(outbox.replay(id)).isTrue();
        var replay = outbox.claim().orElseThrow();
        assertThat(replay.event().id()).isEqualTo(id);
        outbox.delivered(replay);
        assertThat(outbox.stats().failed()).isZero();
    }
}
