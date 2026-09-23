package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.DurableOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class ExportSourceExecutionTest {
    @Test void existingFiveSecondDispatcherRetriesTheSameEventWithoutStartingAnotherSourceTask() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(data);
        var manager = new DataSourceTransactionManager(data);
        var tx = new TransactionTemplate(manager);
        jdbc.execute(ExportSnapshotStore.SCHEMA); jdbc.execute(DurableConsumer.SCHEMA); jdbc.execute(DurableOutbox.SCHEMA);
        var sourceData = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var senderJdbc = new JdbcTemplate(sourceData);
        var senderTx = new TransactionTemplate(new DataSourceTransactionManager(sourceData));
        senderJdbc.execute(DurableOutbox.SCHEMA);
        var clock = Clock.systemUTC();
        var mapper = new ObjectMapper().findAndRegisterModules();
        var protocol = new AccountExportProtocol(mapper);
        var snapshots = new ExportSnapshotStore(jdbc, tx, mapper, clock, "community");
        var receipts = new DurableOutbox(jdbc, tx, "community", clock);
        var sender = new DurableOutbox(senderJdbc, senderTx, "auth", clock);
        var blocked = new java.util.concurrent.atomic.AtomicBoolean(true);
        var cleaned = new CountDownLatch(1);
        var firstWorker = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var calls = new AtomicInteger();
        var participant = new ExportParticipant("community", snapshots, (user, output) -> {
            calls.incrementAndGet();
            firstWorker.compareAndSet(null,Thread.currentThread());
            output.jsonLines("owned", rows -> rows.add(Map.of("ok", true)));
            if (blocked.get()) {
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("cancelled", interrupted); }
            }
        }, new DurableConsumer(jdbc, tx), receipts, protocol, clock);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        try (var execution = new ExportSourceExecution(manager, Duration.ofMillis(1500), Duration.ofSeconds(1), Duration.ofMillis(200))) {
            server.createContext("/events", exchange -> {
                int status = 204;
                try {
                    var event = protocol.event(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    execution.deliver(event, () -> {
                        try { participant.accept(event); }
                        finally { if (Thread.currentThread().isInterrupted()) cleaned.countDown(); }
                    });
                } catch (RuntimeException expected) { status = 503; }
                exchange.sendResponseHeaders(status, -1); exchange.close();
            });
            server.start();
            var dispatcher = new com.chanter.common.events.OutboxDispatcher(sender, mapper,
                    Map.of("lifecycle-community", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/events")),
                    "test-lifecycle-internal-token-at-least-32-characters");
            var now = clock.instant();
            var request = new ExportSnapshotStore.Request(UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(3600));
            senderTx.executeWithoutResult(status -> sender.append("lifecycle-community", AccountExportProtocol.REQUESTED,
                    AccountExportProtocol.key(request.jobId()), protocol.encode(request)));
            long started = System.nanoTime();
            dispatcher.drain();
            senderJdbc.update("UPDATE durable_outbox SET available_at=CURRENT_TIMESTAMP");
            dispatcher.drain();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
            assertThat(calls).hasValue(1);
            assertThat(cleaned.await(3, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> jdbc.queryForObject("SELECT id FROM data_export_lock WHERE id=1 FOR UPDATE", Integer.class));
            // The callback's finally precedes task admission release. Wait for that actual worker to exit.
            assertThat(firstWorker.get().join(Duration.ofSeconds(3))).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox", Integer.class)).isZero();
            blocked.set(false);
            long retryDeadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
            do {
                senderJdbc.update("UPDATE durable_outbox SET available_at=CURRENT_TIMESTAMP");
                dispatcher.drain();
                if("DELIVERED".equals(senderJdbc.queryForObject("SELECT status FROM durable_outbox",String.class))) break;
                Thread.sleep(10);
            } while(System.nanoTime()<retryDeadline);
            assertThat(senderJdbc.queryForObject("SELECT status FROM durable_outbox", String.class)).isEqualTo("DELIVERED");
            assertThat(calls).hasValue(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox", Integer.class)).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void retainedReadAdmissionAndDeadlineBoundTheWholeAuthorityPass() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var entered = new CountDownLatch(4);
        var rolledBack = new AtomicInteger();
        try (var execution = new ExportSourceExecution(new DataSourceTransactionManager(data), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMillis(100));
             var callers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 4; index++) futures.add(callers.submit(() -> {
                assertThatThrownBy(() -> execution.read(() -> {
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override public void afterCompletion(int status) {
                                    assertThat(status).isEqualTo(STATUS_ROLLED_BACK); rolledBack.incrementAndGet();
                                }
                            });
                    entered.countDown();
                    // Simulates a sequence of individually fast current-authority calls across many retained scopes.
                    for (int item = 0; item < 10_000; item++) { Thread.sleep(20); ExportSourceExecution.check(); }
                    return true;
                })).isInstanceOf(RuntimeException.class);
            }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> execution.read(() -> true)).isInstanceOf(ResponseStatusException.class);
            for (var future : futures) future.get(3, TimeUnit.SECONDS);
            assertThat(rolledBack).hasValue(4);
            assertThat(execution.read(() -> true)).isTrue();
        }
    }

    @Test void virtualSocketCancellationRollsBackAndReleasesGlobalLocksBeforeAnotherAccountStarts() throws Exception {
        var data = new TrackingDataSource(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=500", "sa", ""));
        var jdbc = new JdbcTemplate(data);
        var manager = new DataSourceTransactionManager(data);
        var tx = new TransactionTemplate(manager);
        jdbc.execute(ExportSnapshotStore.SCHEMA); jdbc.execute(DurableConsumer.SCHEMA); jdbc.execute(DurableOutbox.SCHEMA);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var clock = Clock.systemUTC();
        var snapshots = new ExportSnapshotStore(jdbc, tx, mapper, clock, "community");
        var protocol = new AccountExportProtocol(mapper);
        var outbox = new DurableOutbox(jdbc, tx, "community", clock);
        var entered = new CountDownLatch(1);
        var disconnected = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/drip", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                while (true) {
                    exchange.getResponseBody().write(65); exchange.getResponseBody().flush();
                    Thread.sleep(30);
                }
            } catch (IOException expected) { disconnected.countDown(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        var now = clock.instant();
        var first = new ExportSnapshotStore.Request(UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(3600));
        var second = new ExportSnapshotStore.Request(UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(3600));
        var firstEvent = event(first, protocol);
        var secondEvent = event(second, protocol);
        var participant = new ExportParticipant("community", snapshots, (user, writer) -> {
            assertThat(data.borrowed).hasValue(1);
            calls.incrementAndGet();
            writer.jsonLines("owned", rows -> rows.add(Map.of("value", "private")));
            if (user.equals(first.accountId())) {
                entered.countDown();
                var connection = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/drip").toURL().openConnection();
                connection.setReadTimeout(5000);
                try (var input = connection.getInputStream()) { input.readNBytes(100_000); }
            }
        }, new DurableConsumer(jdbc, tx), outbox, protocol, clock);
        try (var execution = new ExportSourceExecution(manager, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(100))) {
            assertThatThrownBy(() -> execution.deliver(firstEvent, () -> participant.accept(firstEvent))).isInstanceOf(ResponseStatusException.class);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> execution.deliver(firstEvent, () -> participant.accept(firstEvent))).isInstanceOf(ResponseStatusException.class);
            long rejected = System.nanoTime();
            for (int i = 0; i < 100; i++)
                assertThatThrownBy(() -> execution.deliver(secondEvent, () -> participant.accept(secondEvent))).isInstanceOf(ResponseStatusException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - rejected)).isLessThan(Duration.ofSeconds(1));
            assertThat(calls).hasValue(1);
            assertThat(disconnected.await(3, TimeUnit.SECONDS)).isTrue();
            // A separate connection must acquire both locks after the interrupted transaction unwinds.
            tx.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM data_export_lock WHERE id=1 FOR UPDATE", Integer.class);
                jdbc.queryForObject("SELECT id FROM durable_consumer_lock WHERE id=1 FOR UPDATE", Integer.class);
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_event_cursor", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox", Integer.class)).isZero();
            assertThat(data.borrowed).hasValue(0);
            execution.deliver(secondEvent, () -> participant.accept(secondEvent));
            execution.deliver(secondEvent, () -> participant.accept(secondEvent));
            assertThat(calls).hasValue(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox", Integer.class)).isEqualTo(1);
            assertThat(snapshots.manifest(second.jobId(), second.accountId()).entries()).hasSize(1);
        } finally { server.stop(0); }
    }

    @Test void aLongJdbcProjectionIsCancelledByItsStatementTimeoutAndLeavesNoPartialSnapshot() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(data);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(ExportSnapshotStore.SCHEMA);
        var clock = Clock.systemUTC();
        var snapshots = new ExportSnapshotStore(jdbc, tx, new ObjectMapper().findAndRegisterModules(), clock, "community");
        var now = clock.instant();
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(3600));
        long started = System.nanoTime();
        assertThatThrownBy(() -> snapshots.capture(request, output -> JdbcExportRows.write(jdbc, output, "slow",
                "SELECT SUM(SQRT(X)) AS total FROM SYSTEM_RANGE(1, 1000000000)"))).isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(6));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots", Integer.class)).isZero();
        snapshots.capture(request, output -> output.jsonLines("ok", rows -> rows.add(Map.of("done", true))));
    }

    private static DurableEvent event(ExportSnapshotStore.Request request, AccountExportProtocol protocol) {
        return new DurableEvent(UUID.randomUUID(), 1, "auth", 1, AccountExportProtocol.REQUESTED,
                AccountExportProtocol.key(request.jobId()), protocol.encode(request));
    }

    private static final class TrackingDataSource extends org.springframework.jdbc.datasource.DelegatingDataSource {
        final AtomicInteger borrowed = new AtomicInteger();
        TrackingDataSource(javax.sql.DataSource delegate) { super(delegate); }
        @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
            var connection = super.getConnection();
            borrowed.incrementAndGet();
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class}, (proxy, method, arguments) -> {
                        try { return method.invoke(connection, arguments); }
                        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                        finally {
                            if (method.getName().equals("close") && closed.compareAndSet(false, true)) borrowed.decrementAndGet();
                        }
                    });
        }
    }
}
