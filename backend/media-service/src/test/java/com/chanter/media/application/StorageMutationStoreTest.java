package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class StorageMutationStoreTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private StorageMutationStore store;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void database() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE media_storage_budget(id INT PRIMARY KEY, storage_namespace VARCHAR(64))");
        jdbc.update("INSERT INTO media_storage_budget VALUES(1,?)", "a".repeat(64));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__resource_recovery_maintenance.sql")).execute(source);
        transactions = new DataSourceTransactionManager(source);
        store = restarted();
    }
    private StorageMutationStore restarted() { return new StorageMutationStore(jdbc, transactions, clock); }
    private String key() { return "resources/v1/" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/" + UUID.randomUUID(); }

    @Test void fenceSurvivesRestartAndAllowsOnlyTheOriginalInvocationToSettle() {
        var mutation = store.begin(key(), StorageMutationStore.Operation.PUT);
        var inventory = UUID.randomUUID();
        assertThat(store.fence(inventory).unsettledMutations()).isEqualTo(1);
        assertThatThrownBy(() -> restarted().begin(key(), StorageMutationStore.Operation.DELETE)).hasMessageContaining("maintenance");
        store.uncertain(mutation);
        assertThat(restarted().receipt(inventory).unsettledMutations()).isEqualTo(1);
        restarted().settled(mutation);
        assertThat(store.receipt(inventory).unsettledMutations()).isZero();
        assertThat(store.fence(inventory).startedAt()).isEqualTo(clock.instant());
        assertThatThrownBy(() -> store.release(UUID.randomUUID())).hasMessageContaining("identity");
        assertThatThrownBy(() -> store.fence(UUID.randomUUID())).hasMessageContaining("identity");
        restarted().release(inventory);
        assertThat(store.begin(key(), StorageMutationStore.Operation.DELETE)).isNotNull();
    }

    @Test void unknownDeleteIsNotErasedBySameKeyRetryOrMaintenanceRelease() {
        String key = key();
        UUID mutation = store.begin(key, StorageMutationStore.Operation.DELETE);
        store.uncertain(mutation);
        assertThatThrownBy(() -> restarted().begin(key, StorageMutationStore.Operation.PUT)).hasMessageContaining("unsettled");
        UUID inventory = UUID.randomUUID();
        store.fence(inventory);
        assertThatThrownBy(() -> store.release(inventory)).hasMessageContaining("unsettled");
        assertThat(restarted().receipt(inventory).unsettledMutations()).isEqualTo(1);
        assertThatThrownBy(() -> restarted().begin(key, StorageMutationStore.Operation.DELETE)).hasMessageContaining("maintenance");
        assertThat(jdbc.queryForObject("SELECT outcome FROM media_storage_mutations WHERE id=?", String.class, mutation)).isEqualTo("UNKNOWN");
    }

    @Test void concurrentFenceAndDispatchAccountForEveryAcceptedOperation() {
        var start = new CountDownLatch(1);
        var calls = IntStream.range(0, 8).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
            try { start.await(); return restarted().begin(key(), StorageMutationStore.Operation.PUT); }
            catch (IllegalStateException fenced) { assertThat(fenced).hasMessageContaining("maintenance"); return null; }
            catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
        })).toList();
        UUID inventory = UUID.randomUUID();
        start.countDown(); store.fence(inventory);
        long accepted = calls.stream().map(CompletableFuture::join).filter(java.util.Objects::nonNull).count();
        assertThat(store.receipt(inventory).unsettledMutations()).isEqualTo((int) accepted);
        assertThatThrownBy(() -> store.begin(key(), StorageMutationStore.Operation.DELETE)).hasMessageContaining("maintenance");
    }

    @Test void unresolvedOperationCapacityIsBoundedWithoutDiscardingUnknownHistory() {
        var rows = IntStream.range(0, 4096).mapToObj(i -> new Object[] { UUID.randomUUID(), key(),
                java.sql.Timestamp.from(clock.instant()) }).toList();
        jdbc.batchUpdate("INSERT INTO media_storage_mutations VALUES(?,?,'DELETE','UNKNOWN',?)", rows);
        assertThatThrownBy(() -> store.begin(key(), StorageMutationStore.Operation.PUT)).hasMessageContaining("capacity");
        assertThat(store.fence(UUID.randomUUID()).unsettledMutations()).isEqualTo(4096);
    }

    @Test void sourceHookMustHoldItsOwningTransactionAndNeverChangesTheFence() {
        assertThatThrownBy(store::ordinaryWorkAllowed).hasMessageContaining("source transaction");
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThat(tx.<Boolean>execute(status -> store.ordinaryWorkAllowed())).isTrue();
        UUID inventory = UUID.randomUUID(); store.fence(inventory);
        assertThat(tx.<Boolean>execute(status -> store.ordinaryWorkAllowed())).isFalse();
        assertThat(store.receipt(inventory).inventoryId()).isEqualTo(inventory);
    }

    @Test void adapterCannotSuspendASourceTransactionThatAlreadyOwnsTheBudgetLock() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM media_storage_budget WHERE id=1 FOR UPDATE", Integer.class);
            return store.begin(key(), StorageMutationStore.Operation.PUT);
        })).hasMessageContaining("outside source transactions");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations", Integer.class)).isZero();
    }

    @Test void owningLocalAdapterRejectsSourceTransactionBeforeWriting(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var adapter = new com.chanter.media.infra.LocalPrivateResourceStorage(directory.resolve("private").toString(), store);
        var content = directory.resolve("content"); java.nio.file.Files.writeString(content, "fixture");
        String key = key();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM media_storage_budget WHERE id=1 FOR UPDATE", Integer.class);
            try { adapter.put(key, content, UploadValidator.checksum(content)); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        })).hasRootCauseMessage("Physical storage accounting must run outside source transactions");
        assertThat(directory.resolve("private").resolve(key)).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations", Integer.class)).isZero();
    }

    @Test void failedLocalCopyRemovesOnlyItsOwnCreatedObjectAndAllowsRetry(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var root=directory.resolve("private");
        var adapter=new com.chanter.media.infra.LocalPrivateResourceStorage(root.toString(),store);
        String key=key();
        assertThatThrownBy(() -> adapter.put(key,directory.resolve("missing-input"),"a".repeat(64)))
                .isInstanceOf(PrivateResourceStorage.PutFailure.class);
        assertThat(root.resolve(key)).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations",Integer.class)).isZero();
        var content=directory.resolve("content"); java.nio.file.Files.writeString(content,"complete fixture");
        adapter.put(key,content,UploadValidator.checksum(content));
        assertThatThrownBy(() -> adapter.put(key,directory.resolve("missing-input"),"a".repeat(64)))
                .isInstanceOf(PrivateResourceStorage.PutFailure.class);
        assertThat(root.resolve(key)).hasContent("complete fixture");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations",Integer.class)).isZero();
    }

    @Test void removedLocalPartialStillReportsUnknownWhenSettlementCommitFails(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var interrupted=org.mockito.Mockito.spy(store);
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture settlement unavailable")).when(interrupted).settled(org.mockito.ArgumentMatchers.any());
        var adapter=new com.chanter.media.infra.LocalPrivateResourceStorage(directory.toString(),interrupted);
        String key=key();
        assertThatThrownBy(() -> adapter.put(key,directory.resolve("missing"),"a".repeat(64)))
                .isInstanceOfSatisfying(PrivateResourceStorage.PutFailure.class,
                        failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
        assertThat(directory.resolve(key)).doesNotExist();
        assertThat(restarted().fence(UUID.randomUUID()).unsettledMutations()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT outcome FROM media_storage_mutations WHERE object_key=?",String.class,key)).isEqualTo("ACTIVE");
    }

    @Test void remoteUnknownDeleteSurvivesAdapterRestartAndBlocksRedispatch() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var response = new java.util.concurrent.atomic.AtomicInteger(500);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(response.get(), -1); exchange.close();
        });
        server.start();
        var lifecycle = org.mockito.Mockito.mock(ResourceLifecycle.class);
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        String key = key();
        try {
            var first = new com.chanter.media.infra.S3PrivateResourceStorage(lifecycle, store, endpoint, "us-east-1", "fixture-bucket", "fixture-key", "fixture-secret", true);
            try {
                assertThatThrownBy(() -> first.delete(key)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                        failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
            } finally { first.close(); }
            response.set(204);
            var next = new com.chanter.media.infra.S3PrivateResourceStorage(lifecycle, restarted(), endpoint, "us-east-1", "fixture-bucket", "fixture-key", "fixture-secret", true);
            try {
                assertThatThrownBy(() -> next.delete(key)).hasRootCauseMessage("Private object has an unsettled physical operation");
                assertThat(requests.get()).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT outcome FROM media_storage_mutations WHERE object_key=?", String.class, key)).isEqualTo("UNKNOWN");
                next.delete(key());
                response.set(404); next.delete(key());
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations", Integer.class)).isEqualTo(1);
                UUID inventory = UUID.randomUUID(); store.fence(inventory);
                assertThatThrownBy(() -> next.delete(key())).hasRootCauseMessage("Storage maintenance blocks new physical operations");
                assertThat(requests.get()).isEqualTo(3);
            } finally { next.close(); }
        } finally { server.stop(0); }
    }

    @Test void lostSuccessfulProviderSettlementNeverBecomesAZeroOutstandingReceipt(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1); exchange.close();
        }); server.start();
        var interrupted = org.mockito.Mockito.spy(store);
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture settlement commit unavailable")).when(interrupted).settled(org.mockito.ArgumentMatchers.any());
        var adapter = new com.chanter.media.infra.S3PrivateResourceStorage(org.mockito.Mockito.mock(ResourceLifecycle.class), interrupted,
                "http://127.0.0.1:" + server.getAddress().getPort(), "us-east-1", "fixture-bucket", "fixture-key", "fixture-secret", true);
        String key = key();
        try {
            assertThatThrownBy(() -> adapter.delete(key)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                    failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
            var content=directory.resolve("fixture"); java.nio.file.Files.writeString(content,"fixture");
            assertThatThrownBy(() -> adapter.put(key(),content,UploadValidator.checksum(content)))
                    .isInstanceOfSatisfying(PrivateResourceStorage.PutFailure.class,
                            failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
            assertThat(requests.get()).isEqualTo(2);
            assertThat(restarted().fence(UUID.randomUUID()).unsettledMutations()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT outcome FROM media_storage_mutations WHERE object_key=?", String.class, key)).isEqualTo("ACTIVE");
        } finally { adapter.close(); server.stop(0); }
    }

    @Test void remoteAdapterRejectsSourceTransactionBeforeAnyProviderOrBudgetCall() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        var lifecycle = org.mockito.Mockito.mock(ResourceLifecycle.class);
        var adapter = new com.chanter.media.infra.S3PrivateResourceStorage(lifecycle, store,
                "http://127.0.0.1:" + server.getAddress().getPort(), "us-east-1", "fixture-bucket", "fixture-key", "fixture-secret", true);
        try {
            var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM media_storage_budget WHERE id=1 FOR UPDATE", Integer.class);
                try { adapter.delete(key()); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            })).hasRootCauseMessage("Physical storage accounting must run outside source transactions");
            assertThat(requests.get()).isZero();
            org.mockito.Mockito.verify(lifecycle, org.mockito.Mockito.never()).countRequest(org.mockito.ArgumentMatchers.anyBoolean());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations", Integer.class)).isZero();
        } finally { adapter.close(); server.stop(0); }
    }
}
