package com.chanter.auth.lifecycle;

import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalJournal.Checkpoint;
import com.chanter.common.lifecycle.TerminalJournal.Entry;
import com.chanter.common.lifecycle.TerminalJournal.Page;
import com.chanter.common.lifecycle.TerminalJournal.Watermark;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Serial allocation makes every published watermark a contiguous committed prefix, including after rollback. */
public final class TerminalJournalStore {
    public static final String SCHEMA = """
        CREATE TABLE lifecycle_terminal_journal (
            revision BIGINT PRIMARY KEY, event_id UUID NOT NULL UNIQUE,
            target_kind VARCHAR(16) NOT NULL, target_id UUID NOT NULL,
            deleted_at TIMESTAMP WITH TIME ZONE NOT NULL,
            previous_digest VARCHAR(64) NOT NULL, digest VARCHAR(64) NOT NULL,
            UNIQUE(target_kind,target_id)
        );
        CREATE TABLE lifecycle_journal_head (
            id INT PRIMARY KEY, revision BIGINT NOT NULL, digest VARCHAR(64) NOT NULL,
            checkpoint_revision BIGINT NOT NULL DEFAULT 0, checkpoint_digest VARCHAR(64) NOT NULL,
            checkpoint_id UUID, checkpoint_at TIMESTAMP WITH TIME ZONE
        );
        INSERT INTO lifecycle_journal_head(id,revision,digest,checkpoint_digest)
        VALUES (1,0,'0000000000000000000000000000000000000000000000000000000000000000',
        '0000000000000000000000000000000000000000000000000000000000000000')
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    public TerminalJournalStore(JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc; this.tx = tx; this.clock = clock;
    }

    /** Must join the owning terminal mutation. Never publish authority that can outlive a rolled-back deletion. */
    public Entry append(String kind, UUID targetId) {
        TerminalJournal.requireTarget(kind, targetId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Terminal authority requires a source transaction");
        Watermark previous = lock();
        var existing = jdbc.query("SELECT * FROM lifecycle_terminal_journal WHERE target_kind=? AND target_id=?", TerminalJournalStore::entry, kind, targetId);
        if (!existing.isEmpty()) return existing.getFirst();
        long revision = Math.addExact(previous.revision(), 1);
        UUID event = UUID.randomUUID();
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String digest = TerminalJournal.digest(revision, event, kind, targetId, now, previous.digest());
        jdbc.update("INSERT INTO lifecycle_terminal_journal VALUES (?,?,?,?,?,?,?)", revision, event, kind, targetId,
                Timestamp.from(now), previous.digest(), digest);
        jdbc.update("UPDATE lifecycle_journal_head SET revision=?,digest=? WHERE id=1", revision, digest);
        return new Entry(revision, event, kind, targetId, "DELETE", now, previous.digest(), digest);
    }

    public Page page(long after, Long through, int limit) {
        if (after < 0 || limit < 1 || limit > TerminalJournal.MAX_PAGE) throw new IllegalArgumentException("Invalid journal page request");
        // Appends are immutable. Pinning a committed head avoids holding the global append lock while paging.
        Watermark head = jdbc.queryForObject("SELECT revision,digest FROM lifecycle_journal_head WHERE id=1",
                (rs, row) -> new Watermark(rs.getLong(1), rs.getString(2)));
        long upper = through == null ? head.revision() : through;
        if (upper < after || upper > head.revision()) throw new IllegalArgumentException("Invalid journal watermark");
        var entries = jdbc.query("SELECT * FROM lifecycle_terminal_journal WHERE revision>? AND revision<=? ORDER BY revision LIMIT ?",
                TerminalJournalStore::entry, after, upper, limit);
        Watermark start = watermark(after);
        Watermark end = entries.isEmpty() ? start : new Watermark(entries.getLast().revision(), entries.getLast().digest());
        Page page = new Page(1, start, watermark(upper), entries, end);
        page.validate();
        return page;
    }

    /** The caller owns durable external storage. A matching acknowledgement records its claim, not storage attestation. */
    public Checkpoint acknowledge(Checkpoint checkpoint) {
        checkpoint.validate();
        return tx.execute(status -> {
            Watermark head = lock();
            if (checkpoint.revision() > head.revision() || !watermark(checkpoint.revision()).digest().equals(checkpoint.digest()))
                throw new IllegalArgumentException("Checkpoint does not match committed authority");
            Checkpoint current = checkpoint();
            if (current != null && current.revision() > checkpoint.revision()) return current;
            if (current != null && current.revision() == checkpoint.revision()) {
                if (!current.equals(checkpoint)) throw new IllegalArgumentException("Checkpoint identity changed");
                return current;
            }
            jdbc.update("""
                UPDATE lifecycle_journal_head SET checkpoint_revision=?,checkpoint_digest=?,checkpoint_id=?,checkpoint_at=? WHERE id=1
                """, checkpoint.revision(), checkpoint.digest(), checkpoint.checkpointId(), Timestamp.from(clock.instant()));
            return checkpoint;
        });
    }

    public boolean replicated(long revision) {
        if (revision < 1) return false;
        Checkpoint checkpoint = checkpoint();
        return checkpoint != null && checkpoint.revision() >= revision;
    }
    public Checkpoint checkpoint() {
        return jdbc.queryForObject("SELECT checkpoint_revision,checkpoint_digest,checkpoint_id FROM lifecycle_journal_head WHERE id=1",
                (rs, row) -> rs.getObject(3) == null ? null : new Checkpoint(rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class)));
    }
    private Watermark watermark(long revision) {
        if (revision == 0) return new Watermark(0, TerminalJournal.GENESIS);
        var rows = jdbc.query("SELECT digest FROM lifecycle_terminal_journal WHERE revision=?", (rs, row) -> rs.getString(1), revision);
        if (rows.size() != 1) throw new IllegalArgumentException("Unknown journal revision");
        return new Watermark(revision, rows.getFirst());
    }
    private Watermark lock() {
        return jdbc.queryForObject("SELECT revision,digest FROM lifecycle_journal_head WHERE id=1 FOR UPDATE",
                (rs, row) -> new Watermark(rs.getLong(1), rs.getString(2)));
    }
    private static Entry entry(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getLong("revision"), rs.getObject("event_id", UUID.class), rs.getString("target_kind"),
                rs.getObject("target_id", UUID.class), "DELETE", rs.getTimestamp("deleted_at").toInstant(), rs.getString("previous_digest"), rs.getString("digest"));
    }
}
