package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.lifecycle.TerminalJournal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class TerminalJournalStoreTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    TerminalJournalStore journal;
    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data); tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(TerminalJournalStore.SCHEMA);
        journal = new TerminalJournalStore(jdbc, tx, Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC));
    }
    @Test void terminalMutationRollbackCannotPublishAuthorityOrLeaveRevisionGaps() {
        UUID target = UUID.randomUUID();
        assertThatThrownBy(() -> journal.append("ACCOUNT", target)).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> { journal.append("ACCOUNT", target); status.setRollbackOnly(); });
        assertThat(journal.page(0, null, 1).through().revision()).isZero();
        var first = tx.execute(status -> journal.append("ACCOUNT", target));
        assertThat(first.revision()).isEqualTo(1);
        var duplicate = tx.execute(status -> journal.append("ACCOUNT", target));
        assertThat(duplicate).isEqualTo(first);
        assertThat(journal.page(0, null, 10).entries()).containsExactly(first);
        assertThat(journal.replicated(1)).isFalse();
    }
    @Test void pagesPinAContiguousDigestEvenAsNewEntriesCommitAndRejectTampering() {
        for (int index = 0; index < 3; index++) tx.executeWithoutResult(status -> journal.append("RESOURCE", UUID.randomUUID()));
        var first = journal.page(0, null, 2); first.validate();
        tx.executeWithoutResult(status -> journal.append("STUDY_SERVER", UUID.randomUUID()));
        var last = journal.page(first.next().revision(), first.through().revision(), 2); last.validate();
        assertThat(first.through()).isEqualTo(last.through());
        assertThat(last.next()).isEqualTo(first.through());
        assertThat(last.entries()).hasSize(1);
        assertThat(first.next()).isEqualTo(last.after());
        var corrupted = new TerminalJournal.Page(TerminalJournal.SCHEMA_VERSION, first.after(), first.through(), last.entries(), first.next());
        assertThatThrownBy(corrupted::validate).isInstanceOf(IllegalArgumentException.class);
        var entry = first.entries().getFirst();
        var changed = new TerminalJournal.Entry(entry.revision(), entry.eventId(), "ACCOUNT", entry.targetId(), "DELETE", entry.deletedAt(), TerminalJournal.RETENTION_POLICY, entry.previousDigest(), entry.digest());
        assertThatThrownBy(changed::validate).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> journal.page(0, null, 501)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> journal.page(0, 5L, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.execute(status -> journal.append("SESSION", UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void checkpointMustMatchCommittedDigestAndOnlyAdvancesWithStableAcknowledgementIdentity() {
        var first = tx.execute(status -> journal.append("ACCOUNT", UUID.randomUUID()));
        var good = new TerminalJournal.Checkpoint(1, first.digest(), UUID.randomUUID());
        assertThatThrownBy(() -> journal.acknowledge(new TerminalJournal.Checkpoint(1, "f".repeat(64), UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> journal.acknowledge(new TerminalJournal.Checkpoint(2, first.digest(), UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
        assertThat(journal.replicated(1)).isFalse();
        assertThat(journal.acknowledge(good)).isEqualTo(good);
        assertThat(journal.acknowledge(good)).isEqualTo(good);
        assertThat(journal.replicated(1)).isTrue();
        assertThatThrownBy(() -> journal.acknowledge(new TerminalJournal.Checkpoint(1, first.digest(), UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
        var second = tx.execute(status -> journal.append("RESOURCE", UUID.randomUUID()));
        assertThat(journal.replicated(2)).isFalse();
        var newer = journal.acknowledge(new TerminalJournal.Checkpoint(2, second.digest(), UUID.randomUUID()));
        assertThat(journal.acknowledge(good)).isEqualTo(newer);
        assertThat(journal.replicated(2)).isTrue();
    }
    @Test void concurrentCommitCannotSkipAnUncommittedEarlierDeletion() throws Exception {
        var appended = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> tx.execute(status -> {
                var entry = journal.append("ACCOUNT", UUID.randomUUID()); appended.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
                return entry;
            }));
            assertThat(appended.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> tx.execute(status -> journal.append("RESOURCE", UUID.randomUUID())));
            assertThat(journal.page(0, null, 10).through().revision()).isZero();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).revision()).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS).revision()).isEqualTo(2);
            var page = journal.page(0, null, 10); page.validate();
            assertThat(page.entries()).hasSize(2);
        } finally { release.countDown(); }
    }

    @Test void recoveryPreservesTheOriginalCanonicalChainAndRollsBackWithItsParticipantMutation() {
        tx.executeWithoutResult(status -> journal.append("ACCOUNT", UUID.randomUUID()));
        tx.executeWithoutResult(status -> journal.append("RESOURCE", UUID.randomUUID()));
        var external = journal.page(0, null, 100);
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var restoredJdbc = new JdbcTemplate(data);
        var restoredTx = new TransactionTemplate(new DataSourceTransactionManager(data));
        restoredJdbc.execute(TerminalJournalStore.SCHEMA);
        restoredJdbc.execute(com.chanter.common.lifecycle.TerminalReapplyStore.SCHEMA);
        var restored = new TerminalJournalStore(restoredJdbc, restoredTx, Clock.systemUTC());
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        var participant = new com.chanter.common.lifecycle.TerminalReapplyStore(restoredJdbc, restoredTx, "auth", entry -> {
            if (entry.revision() == 2 && fail.get()) throw new IllegalStateException("closure failed");
            return com.chanter.common.lifecycle.TerminalReapplyStore.Cleanup.PENDING;
        });
        assertThatThrownBy(() -> restored.restore(external, () -> participant.reapply(external))).isInstanceOf(IllegalStateException.class);
        assertThat(restored.page(0, null, 100).through().revision()).isZero();
        assertThat(participant.receipt().authority().revision()).isZero();
        assertThat(restoredJdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets", Integer.class)).isZero();
        fail.set(false);
        var receipt = restored.restore(external, () -> participant.reapply(external));
        assertThat(receipt.authority()).isEqualTo(external.through());
        assertThat(receipt.pendingTargets()).isEqualTo(2);
        assertThat(restored.restore(external, () -> participant.reapply(external))).isEqualTo(receipt);
        assertThat(restored.page(0, null, 100).entries()).isEqualTo(external.entries());
        assertThat(restored.checkpoint()).isNull();
        var next = restoredTx.execute(status -> restored.append("STUDY_SERVER", UUID.randomUUID()));
        assertThat(next.revision()).isEqualTo(3);
        assertThat(next.previousDigest()).isEqualTo(external.through().digest());
        restored.page(0, null, 100).validate();
    }
}
