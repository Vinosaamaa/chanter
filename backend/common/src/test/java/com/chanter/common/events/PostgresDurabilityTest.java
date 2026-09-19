package com.chanter.common.events;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="DURABLE_EVENTS_TEST_JDBC_URL", matches=".+")
class PostgresDurabilityTest {
    @Test void concurrentClaimsAndDuplicateConsumersCommitOnlyOnceAndSurviveReconnect() throws Exception {
        String base = System.getenv("DURABLE_EVENTS_TEST_JDBC_URL");
        String schema = "events_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(base, "events_test", "events_test"));
        admin.execute("CREATE SCHEMA " + schema);
        String url = base + (base.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try {
            var ds = new DriverManagerDataSource(url, "events_test", "events_test");
            var jdbc = new JdbcTemplate(ds);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute(DurableOutbox.SCHEMA);
            jdbc.execute(DurableConsumer.SCHEMA);
            jdbc.execute("CREATE TABLE effects (id INT PRIMARY KEY)");
            var outbox = new DurableOutbox(jdbc, tx, "community", Clock.systemUTC());
            tx.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO effects VALUES (1)");
                outbox.append("search", "EVENT", "EVENT:rollback", "{}");
                status.setRollbackOnly();
            });
            assertThat(outbox.claim()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effects", Integer.class)).isZero();
            tx.executeWithoutResult(status -> outbox.append("search", "EVENT", "EVENT:1", "{}"));
            var gate = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Callable<java.util.Optional<DurableOutbox.Delivery>> claim = () -> { gate.await(); return outbox.claim(); };
                var first = executor.submit(claim);
                var second = executor.submit(claim);
                gate.countDown();
                var claims = java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                        .flatMap(java.util.Optional::stream).toList();
                assertThat(claims).hasSize(1);
                var delivery = claims.getFirst();
                var consumer = new DurableConsumer(jdbc, tx);
                Callable<Boolean> apply = () -> consumer.apply(delivery.event(), false, () -> jdbc.update("INSERT INTO effects VALUES (1)"));
                var a = executor.submit(apply);
                var b = executor.submit(apply);
                assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
                outbox.failed(delivery, "HTTP_503");
                jdbc.update("UPDATE durable_outbox SET available_at=CURRENT_TIMESTAMP - INTERVAL '1 second'");
                var reconnectDs = new DriverManagerDataSource(url, "events_test", "events_test");
                var reconnectJdbc = new JdbcTemplate(reconnectDs);
                var reconnectTx = new TransactionTemplate(new DataSourceTransactionManager(reconnectDs));
                var restarted = new DurableOutbox(reconnectJdbc, reconnectTx, "community", Clock.systemUTC());
                var retry = restarted.claim().orElseThrow();
                assertThat(retry.event().id()).isEqualTo(delivery.event().id());
                assertThat(new DurableConsumer(reconnectJdbc, reconnectTx).apply(retry.event(), false,
                        () -> { throw new AssertionError("duplicate after reconnect"); })).isFalse();
                restarted.delivered(retry);
                assertThat(restarted.stats().delivered()).isEqualTo(1);
                assertThat(reconnectJdbc.queryForObject("SELECT COUNT(*) FROM effects", Integer.class)).isEqualTo(1);
            }
        } finally { admin.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
}
