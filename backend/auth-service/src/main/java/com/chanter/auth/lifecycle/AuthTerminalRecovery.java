package com.chanter.auth.lifecycle;

import com.chanter.auth.application.RefreshTokenRepository;
import com.chanter.common.lifecycle.RecoveryInvalidationStore;
import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Source-owned effects. Recovery must keep public writers closed until every participant proves the same authority. */
public final class AuthTerminalRecovery {
    private final JdbcTemplate jdbc;
    private final TerminalJournalStore journal;
    private final TerminalReapplyStore participant;
    private final RecoveryInvalidationStore invalidations;
    private final RefreshTokenRepository sessions;
    private final AccountExportJobs exports;

    public AuthTerminalRecovery(JdbcTemplate jdbc, TransactionTemplate tx, TerminalJournalStore journal,
            RefreshTokenRepository sessions, AccountExportJobs exports, Clock clock) {
        this.jdbc = jdbc; this.journal = journal; this.sessions = sessions; this.exports = exports;
        participant = new TerminalReapplyStore(jdbc, tx, "auth", this::apply);
        invalidations = new RecoveryInvalidationStore(jdbc, clock, "auth");
    }

    public TerminalReapplyStore.Receipt reapply(TerminalJournal.Page page) {
        return journal.restore(page, () -> participant.reapply(page));
    }
    public TerminalReapplyStore.Receipt receipt() { return participant.receipt(); }

    public RecoveryInvalidationStore.Receipt invalidate(RecoveryInvalidationStore.Request request) {
        request.validate();
        return journal.atHead(request.authority(), () -> invalidations.invalidate(request, participant.receipt().authority(), this::invalidateAll));
    }

    private TerminalReapplyStore.Cleanup apply(TerminalJournal.Entry entry) {
        if (!"ACCOUNT".equals(entry.targetKind())) return TerminalReapplyStore.Cleanup.COMPLETE;
        sessions.revokeAllForUser(entry.targetId(), entry.deletedAt());
        exports.cancelAccount(entry.targetId());
        jdbc.update("DELETE FROM lifecycle_export_downloads WHERE account_id=?", entry.targetId());
        jdbc.update("UPDATE auth_email_tokens SET used_at=? WHERE user_id=? AND used_at IS NULL", Timestamp.from(entry.deletedAt()), entry.targetId());
        jdbc.update("""
            UPDATE auth_email_outbox SET recipient=NULL,subject=NULL,body_text=NULL,status='EXPIRED',completed_at=?
            WHERE status='PENDING' AND recipient IN (SELECT email FROM auth_users WHERE id=?)
            """, Timestamp.from(entry.deletedAt()), entry.targetId());
        return TerminalReapplyStore.Cleanup.PENDING;
    }

    private void invalidateAll(Instant now) {
        // The restore orchestrator keeps writers closed. This transaction records effects and receipt together.
        var timestamp = Timestamp.from(now);
        jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE revoked_at IS NULL", timestamp);
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at=? WHERE revoked_at IS NULL", timestamp);
        jdbc.update("UPDATE auth_email_tokens SET used_at=? WHERE used_at IS NULL", timestamp);
        jdbc.update("DELETE FROM lifecycle_export_downloads");
        jdbc.update("""
            UPDATE auth_email_outbox SET recipient=NULL,subject=NULL,body_text=NULL,status='EXPIRED',completed_at=? WHERE status='PENDING'
            """, timestamp);
    }
}
