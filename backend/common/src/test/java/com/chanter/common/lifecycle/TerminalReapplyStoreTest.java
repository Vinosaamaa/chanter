package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.events.DurableOutbox;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class TerminalReapplyStoreTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DurableOutbox outbox;

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data); tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(TerminalReapplyStore.SCHEMA); jdbc.execute(DurableOutbox.SCHEMA);
        jdbc.execute("CREATE TABLE owned_content(id UUID PRIMARY KEY, body VARCHAR(100), inaccessible BOOLEAN DEFAULT FALSE)");
        outbox = new DurableOutbox(jdbc, tx, "media", Clock.systemUTC());
    }

    @Test void mutationFenceDurableCleanupAndPrefixCommitOnceWhilePendingAndPreservedRemainExplicit() {
        var first = entry(1, "ACCOUNT", TerminalJournal.GENESIS);
        var second = entry(2, "RESOURCE", first.digest());
        for (var entry : List.of(first, second)) jdbc.update("INSERT INTO owned_content(id,body) VALUES (?,?)", entry.targetId(), "private");
        var store = new TerminalReapplyStore(jdbc, tx, "media", entry -> {
            jdbc.update("UPDATE owned_content SET inaccessible=TRUE WHERE id=?", entry.targetId());
            outbox.append("lifecycle-media", "TERMINAL_CLEANUP", "terminal:" + entry.targetId(), "{}");
            return entry == first ? TerminalReapplyStore.Cleanup.PENDING : TerminalReapplyStore.Cleanup.PRESERVED;
        });
        var page = page(List.of(first, second));
        var receipt = store.reapply(page);
        assertThat(store.reapply(page)).isEqualTo(receipt);
        assertThat(receipt.authority()).isEqualTo(new TerminalJournal.Watermark(2, second.digest()));
        assertThat(receipt.pendingTargets()).isEqualTo(1);
        assertThat(receipt.preservedTargets()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM owned_content WHERE inaccessible=TRUE", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.requireWritable("ACCOUNT", first.targetId())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM owned_content WHERE id=?", first.targetId());
            assertThat(store.recordCleanup(first, TerminalReapplyStore.Cleanup.PENDING, TerminalReapplyStore.Cleanup.COMPLETE)).isTrue();
        });
        assertThat(store.receipt().pendingTargets()).isZero();
        assertThat(store.receipt().preservedTargets()).isEqualTo(1);
        assertThat(store.terminal("ACCOUNT", first.targetId())).isTrue();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.recordCleanup(first,
                TerminalReapplyStore.Cleanup.COMPLETE, TerminalReapplyStore.Cleanup.PENDING))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void partialFailureRollsBackEveryFenceSourceMutationAndReceiptThenTheSamePageRetries() {
        var first = entry(1, "ACCOUNT", TerminalJournal.GENESIS);
        var second = entry(2, "STUDY_SERVER", first.digest());
        var fail = new AtomicBoolean(true);
        var store = new TerminalReapplyStore(jdbc, tx, "community", entry -> {
            jdbc.update("INSERT INTO owned_content(id,body,inaccessible) VALUES (?,'',TRUE)", entry.targetId());
            outbox.append("lifecycle-media", "TERMINAL_CLEANUP", "terminal:" + entry.targetId(), "{}");
            if (entry.revision() == 2 && fail.get()) throw new IllegalStateException("source failed");
            return TerminalReapplyStore.Cleanup.COMPLETE;
        });
        assertThatThrownBy(() -> store.reapply(page(List.of(first, second)))).isInstanceOf(IllegalStateException.class);
        assertThat(store.receipt().authority().revision()).isZero();
        for (var table : List.of("owned_content", "lifecycle_terminal_targets", "lifecycle_reapply_entries", "durable_outbox"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        fail.set(false);
        assertThat(store.reapply(page(List.of(first, second))).authority().revision()).isEqualTo(2);
    }

    @Test void ordinaryDeliveryCanPrecedeRecoveryButGapsChangedTargetsAndChangedPrefixesCannotBeAccepted() {
        var first = entry(1, "ACCOUNT", TerminalJournal.GENESIS);
        var second = entry(2, "RESOURCE", first.digest());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var store = new TerminalReapplyStore(jdbc, tx, "agent", entry -> { calls.incrementAndGet(); return TerminalReapplyStore.Cleanup.COMPLETE; });
        tx.executeWithoutResult(status -> store.applyTerminal(second));
        assertThat(store.receipt().authority().revision()).isZero();
        var after = new TerminalJournal.Watermark(1, first.digest());
        var through = new TerminalJournal.Watermark(2, second.digest());
        assertThatThrownBy(() -> store.reapply(new TerminalJournal.Page(TerminalJournal.SCHEMA_VERSION, after, through, List.of(second), through)))
                .isInstanceOf(IllegalArgumentException.class);
        store.reapply(page(List.of(first, second)));
        assertThat(calls).hasValue(2);
        var changed = entry(1, "ACCOUNT", TerminalJournal.GENESIS);
        assertThatThrownBy(() -> store.reapply(page(List.of(changed)))).isInstanceOf(IllegalArgumentException.class);
        var time = first.deletedAt();
        var differentId = UUID.randomUUID();
        var mismatch = new TerminalJournal.Entry(3, differentId, first.targetKind(), first.targetId(), "DELETE", time, TerminalJournal.RETENTION_POLICY,
                second.digest(), TerminalJournal.digest(3, differentId, first.targetKind(), first.targetId(), time, second.digest()));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.applyTerminal(mismatch))).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(2);
    }

    private static TerminalJournal.Entry entry(long revision, String kind, String previous) {
        var event = UUID.randomUUID(); var target = UUID.randomUUID(); var time = Instant.parse("2026-09-19T00:00:00Z");
        return new TerminalJournal.Entry(revision, event, kind, target, "DELETE", time, TerminalJournal.RETENTION_POLICY, previous,
                TerminalJournal.digest(revision, event, kind, target, time, previous));
    }
    private static TerminalJournal.Page page(List<TerminalJournal.Entry> entries) {
        var last = entries.getLast();
        var end = new TerminalJournal.Watermark(last.revision(), last.digest());
        return new TerminalJournal.Page(TerminalJournal.SCHEMA_VERSION, new TerminalJournal.Watermark(0, TerminalJournal.GENESIS), end, entries, end);
    }
}
