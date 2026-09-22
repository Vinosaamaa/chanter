package com.chanter.common.lifecycle;

import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits terminal source authority and a recovery prefix together. Cleanup completion is separate. */
public final class TerminalReapplyStore {
    public static final String SCHEMA = """
        CREATE TABLE lifecycle_reapply_head (id INT PRIMARY KEY, revision BIGINT NOT NULL, digest VARCHAR(64) NOT NULL);
        INSERT INTO lifecycle_reapply_head VALUES (1,0,'0000000000000000000000000000000000000000000000000000000000000000');
        CREATE TABLE lifecycle_reapply_entries (revision BIGINT PRIMARY KEY, digest VARCHAR(64) NOT NULL);
        CREATE TABLE lifecycle_terminal_targets (
            target_kind VARCHAR(16) NOT NULL CHECK(target_kind IN ('ACCOUNT','STUDY_SERVER','RESOURCE')), target_id UUID NOT NULL,
            revision BIGINT NOT NULL UNIQUE CHECK(revision>0), event_id UUID NOT NULL UNIQUE, digest VARCHAR(64) NOT NULL,
            deleted_at TIMESTAMP WITH TIME ZONE NOT NULL, cleanup_state VARCHAR(16) NOT NULL CHECK(cleanup_state IN ('PENDING','PRESERVED','COMPLETE')),
            PRIMARY KEY(target_kind,target_id)
        )
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String source;
    private final Mutation mutation;

    public TerminalReapplyStore(JdbcTemplate jdbc, TransactionTemplate tx, String source, Mutation mutation) {
        if (!AccountExportProtocol.SOURCES.contains(source)) throw new IllegalArgumentException("Unknown terminal source");
        this.jdbc = jdbc; this.tx = tx; this.source = source; this.mutation = java.util.Objects.requireNonNull(mutation);
    }

    /** The trusted recovery caller supplies the externally verified chain, never a browser. */
    public Receipt reapply(TerminalJournal.Page page) {
        page.validate();
        return tx.execute(status -> {
            var current = lock();
            if (page.after().revision() > current.revision() || !watermark(page.after().revision()).equals(page.after()))
                throw new IllegalArgumentException("Journal prefix is missing or inconsistent");
            for (var entry : page.entries()) {
                ExportSourceExecution.check();
                if (entry.revision() <= current.revision()) {
                    if (!watermark(entry.revision()).digest().equals(entry.digest()))
                        throw new IllegalArgumentException("Committed journal prefix changed");
                    continue;
                }
                if (entry.revision() != current.revision() + 1 || !entry.previousDigest().equals(current.digest()))
                    throw new IllegalArgumentException("Journal prefix is discontinuous");
                applyLocked(entry);
                jdbc.update("INSERT INTO lifecycle_reapply_entries VALUES (?,?)", entry.revision(), entry.digest());
                current = new TerminalJournal.Watermark(entry.revision(), entry.digest());
            }
            jdbc.update("UPDATE lifecycle_reapply_head SET revision=?,digest=? WHERE id=1", current.revision(), current.digest());
            return receipt(current);
        });
    }

    /** Ordinary durable deletion delivery uses the same target fence, without claiming an entire recovery prefix. */
    public void applyTerminal(TerminalJournal.Entry entry) {
        entry.validate();
        requireTransaction();
        lock();
        applyLocked(entry);
    }

    private void applyLocked(TerminalJournal.Entry entry) {
        var existing = jdbc.query("SELECT revision,event_id,digest FROM lifecycle_terminal_targets WHERE target_kind=? AND target_id=?",
                (rs, row) -> new Applied(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3)), entry.targetKind(), entry.targetId());
        if (!existing.isEmpty()) {
            var saved = existing.getFirst();
            if (saved.revision() != entry.revision() || !saved.eventId().equals(entry.eventId()) || !saved.digest().equals(entry.digest()))
                throw new IllegalArgumentException("Terminal target authority changed");
            return;
        }
        // The source mutation must establish access denial and durable cleanup before this transaction commits.
        Cleanup cleanup = java.util.Objects.requireNonNull(mutation.apply(entry));
        ExportSourceExecution.check();
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES (?,?,?,?,?,?,?)", entry.targetKind(), entry.targetId(),
                entry.revision(), entry.eventId(), entry.digest(), Timestamp.from(entry.deletedAt()), cleanup.name());
    }

    public boolean terminal(String kind, UUID target) {
        TerminalJournal.requireTarget(kind, target);
        return jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind=? AND target_id=?",
                Integer.class, kind, target) != 0;
    }

    public Cleanup cleanup(TerminalJournal.Entry entry) {
        requireTransaction(); entry.validate();
        var states=jdbc.query("SELECT cleanup_state FROM lifecycle_terminal_targets WHERE target_kind=? AND target_id=? AND revision=? AND event_id=? AND digest=?",
                (rs,row) -> Cleanup.valueOf(rs.getString(1)),entry.targetKind(),entry.targetId(),entry.revision(),entry.eventId(),entry.digest());
        if(states.size()!=1) throw new IllegalArgumentException("Unknown terminal authority");
        return states.getFirst();
    }

    /** A newly verified scope can finish already-applied authority without inventing another journal entry. */
    public void reconcile(TerminalJournal.Entry entry) {
        requireTransaction(); entry.validate(); lock();
        Cleanup previous=cleanup(entry);
        if(previous!=Cleanup.PENDING) return;
        Cleanup next=java.util.Objects.requireNonNull(mutation.apply(entry));
        ExportSourceExecution.check();
        if(next!=previous && !recordCleanup(entry,previous,next)) throw new IllegalStateException("Terminal reconciliation changed");
    }

    /** Source writes take this lock before their rows, so an in-flight write cannot recreate a terminal target. */
    public void requireWritable(String kind, UUID target) {
        if (!writable(kind, target)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.GONE, "LIFECYCLE_TARGET_DELETED");
    }

    /** A terminal delayed delivery is acknowledged without recreating its payload. The owning transaction holds this lock. */
    public boolean writable(String kind, UUID target) {
        requireTransaction(); lock();
        return !terminal(kind, target);
    }

    /** Called in the transaction that proves cleanup or changes a preservation hold. Completed deletion cannot be undone. */
    public boolean recordCleanup(TerminalJournal.Entry entry, Cleanup previous, Cleanup next) {
        requireTransaction(); entry.validate();
        if (previous == null || next == null || previous == Cleanup.COMPLETE || previous == next
                || previous == Cleanup.PRESERVED && next != Cleanup.PENDING)
            throw new IllegalArgumentException("Invalid cleanup transition");
        return jdbc.update("""
                UPDATE lifecycle_terminal_targets SET cleanup_state=?
                WHERE target_kind=? AND target_id=? AND revision=? AND event_id=? AND digest=? AND cleanup_state=?
                """, next.name(), entry.targetKind(), entry.targetId(), entry.revision(), entry.eventId(), entry.digest(), previous.name()) == 1;
    }

    public Receipt receipt() {
        return tx.execute(status -> receipt(lock()));
    }

    private Receipt receipt(TerminalJournal.Watermark watermark) {
        int pending = jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE revision<=? AND cleanup_state='PENDING'",
                Integer.class, watermark.revision());
        int preserved = jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE revision<=? AND cleanup_state='PRESERVED'",
                Integer.class, watermark.revision());
        return new Receipt(1, source, watermark, pending, preserved);
    }

    private TerminalJournal.Watermark lock() {
        return jdbc.queryForObject("SELECT revision,digest FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",
                (rs, row) -> new TerminalJournal.Watermark(rs.getLong(1), rs.getString(2)));
    }
    private TerminalJournal.Watermark watermark(long revision) {
        if (revision == 0) return new TerminalJournal.Watermark(0, TerminalJournal.GENESIS);
        var saved = jdbc.query("SELECT digest FROM lifecycle_reapply_entries WHERE revision=?", (rs, row) -> rs.getString(1), revision);
        if (saved.size() != 1) throw new IllegalArgumentException("Unknown committed prefix");
        return new TerminalJournal.Watermark(revision, saved.getFirst());
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Terminal mutation requires source transaction");
    }
    public enum Cleanup { PENDING, PRESERVED, COMPLETE }
    /** Database authority and existing durable cleanup only; no network or filesystem effects inside this transaction. */
    @FunctionalInterface public interface Mutation { Cleanup apply(TerminalJournal.Entry entry); }
    public record Receipt(int schemaVersion, String source, TerminalJournal.Watermark authority, int pendingTargets, int preservedTargets) { }
    private record Applied(long revision, UUID eventId, String digest) { }
}
