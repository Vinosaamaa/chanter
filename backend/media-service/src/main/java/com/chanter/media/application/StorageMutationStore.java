package com.chanter.media.application;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable ownership of physical operations; never dispatches or retries provider work. */
@Repository
public class StorageMutationStore {
    private static final int MAX_MUTATIONS = 4096;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    public enum Operation { PUT, DELETE }
    public record Fence(UUID inventoryId, Instant startedAt, String storageNamespaceSha256, int unsettledMutations) { }
    public StorageMutationStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc; this.clock = clock;
        tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(5);
    }

    /** Commits before provider dispatch. The owning adapter must retain this identity until completion. */
    public UUID begin(String key, Operation operation) {
        requireNoSourceTransaction();
        requireKey(key);
        java.util.Objects.requireNonNull(operation);
        return tx.execute(status -> {
            if (lock().inventoryId() != null) throw new IllegalStateException("Storage maintenance blocks new physical operations");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations WHERE object_key=?", Integer.class, key) != 0)
                throw new IllegalStateException("Private object has an unsettled physical operation");
            if (count() >= MAX_MUTATIONS) throw new IllegalStateException("Outstanding storage operation capacity reached");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO media_storage_mutations(id,object_key,operation,outcome,started_at) VALUES(?,?,?,'ACTIVE',?)",
                    id, key, operation.name(), Timestamp.from(clock.instant()));
            return id;
        });
    }

    public void uncertain(UUID mutation) {
        requireNoSourceTransaction();
        requireIdentity(mutation);
        tx.executeWithoutResult(status -> {
            lock();
            if (jdbc.update("UPDATE media_storage_mutations SET outcome='UNKNOWN' WHERE id=?", mutation) != 1)
                throw new IllegalStateException("Unknown storage operation identity");
        });
    }

    /** Only the original adapter invocation's definitive outcome may remove its outstanding record. */
    public void settled(UUID mutation) {
        requireNoSourceTransaction();
        requireIdentity(mutation);
        tx.executeWithoutResult(status -> {
            lock();
            jdbc.update("DELETE FROM media_storage_mutations WHERE id=?", mutation);
        });
    }

    public Fence fence(UUID inventoryId) {
        requireNoSourceTransaction();
        requireIdentity(inventoryId);
        return tx.execute(status -> {
            var current = lock();
            if (current.inventoryId() == null) {
                Instant started = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                jdbc.update("UPDATE media_storage_budget SET maintenance_inventory_id=?,maintenance_started_at=? WHERE id=1",
                        inventoryId, Timestamp.from(started));
                current = new State(inventoryId, started, current.namespace());
            } else requireMatching(current, inventoryId);
            return receipt(current);
        });
    }

    public Fence receipt(UUID inventoryId) {
        requireNoSourceTransaction();
        requireIdentity(inventoryId);
        return tx.execute(status -> {
            var current = lock(); requireMatching(current, inventoryId);
            return receipt(current);
        });
    }

    public void release(UUID inventoryId) {
        requireNoSourceTransaction();
        requireIdentity(inventoryId);
        tx.executeWithoutResult(status -> {
            var current = lock(); requireMatching(current, inventoryId);
            jdbc.update("UPDATE media_storage_budget SET maintenance_inventory_id=NULL,maintenance_started_at=NULL WHERE id=1");
        });
    }

    /** Caller holds its terminal-authority lock first; this budget lock lasts through the owning source transaction. */
    public boolean ordinaryWorkAllowed() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Storage maintenance check requires the source transaction");
        return lock().inventoryId() == null;
    }

    private Fence receipt(State state) { return new Fence(state.inventoryId(), state.startedAt(), state.namespace(), count()); }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations", Integer.class); }
    private State lock() {
        return jdbc.queryForObject("SELECT maintenance_inventory_id,maintenance_started_at,storage_namespace FROM media_storage_budget WHERE id=1 FOR UPDATE",
                (rs, row) -> new State(rs.getObject(1, UUID.class), rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(), rs.getString(3)));
    }
    private static void requireMatching(State state, UUID id) {
        if (!id.equals(state.inventoryId())) throw new IllegalStateException("Storage maintenance identity does not match");
    }
    private static void requireIdentity(UUID id) {
        if (id == null || id.equals(new UUID(0, 0))) throw new IllegalArgumentException("Invalid storage operation identity");
    }
    private static void requireNoSourceTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Physical storage accounting must run outside source transactions");
    }
    private static void requireKey(String key) {
        PrivateResourceStorage.requireKey(key);
        String[] parts = key.split("/");
        for (int i = 2; i < 5; i++) {
            UUID id;
            try { id = UUID.fromString(parts[i]); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid private resource key"); }
            if (!id.toString().equals(parts[i]) || id.equals(new UUID(0, 0))) throw new IllegalArgumentException("Invalid private resource key");
        }
    }
    private record State(UUID inventoryId, Instant startedAt, String namespace) { }
}
