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
        store.release(inventory);
        assertThatThrownBy(() -> restarted().begin(key, StorageMutationStore.Operation.DELETE)).hasMessageContaining("unsettled");
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
}
