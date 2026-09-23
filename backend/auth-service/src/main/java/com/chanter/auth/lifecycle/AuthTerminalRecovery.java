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
        return journal.restore(page, () -> { lockOperatorAuthority(); return participant.reapply(page); });
    }
    public TerminalReapplyStore.Receipt receipt() { return participant.receipt(); }

    /** The coordinator has already appended this exact entry in the same canonical transaction. */
    public TerminalReapplyStore.Cleanup applyCommitted(TerminalJournal.Entry entry) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Normal terminal application requires its canonical transaction");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_journal WHERE revision=? AND event_id=? AND digest=?",
                Integer.class,entry.revision(),entry.eventId(),entry.digest())!=1) throw new IllegalArgumentException("Unknown canonical terminal entry");
        lockOperatorAuthority();
        participant.applyTerminal(entry);
        return participant.cleanup(entry);
    }

    public RecoveryInvalidationStore.Receipt invalidate(RecoveryInvalidationStore.Request request) {
        request.validate();
        return journal.atHead(request.authority(), () -> invalidations.invalidate(request, participant.receipt().authority(), this::invalidateAll));
    }

    private TerminalReapplyStore.Cleanup apply(TerminalJournal.Entry entry) {
        if (!"ACCOUNT".equals(entry.targetKind())) return TerminalReapplyStore.Cleanup.COMPLETE;
        sessions.revokeAllForUser(entry.targetId(), entry.deletedAt());
        exports.cancelAccount(entry.targetId());
        jdbc.update("DELETE FROM lifecycle_export_downloads WHERE account_id=?", entry.targetId());
        jdbc.update("DELETE FROM auth_email_tokens WHERE user_id=?", entry.targetId());
        jdbc.update("""
            UPDATE auth_email_outbox SET recipient=NULL,subject=NULL,body_text=NULL,status='EXPIRED',completed_at=?
            WHERE status='PENDING' AND recipient IN (SELECT email FROM auth_users WHERE id=?)
            """, Timestamp.from(entry.deletedAt()), entry.targetId());
        jdbc.update("DELETE FROM auth_refresh_tokens WHERE user_id=?",entry.targetId());
        jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",entry.targetId());
        jdbc.update("DELETE FROM auth_oauth_accounts WHERE user_id=?",entry.targetId());
        jdbc.update("DELETE FROM moderation_appeal_tokens WHERE user_id=?",entry.targetId());
        jdbc.update("DELETE FROM platform_step_up WHERE user_id=?",entry.targetId());
        jdbc.update("""
            UPDATE platform_operators SET revoked_at=?,factor_ciphertext=NULL,factor_confirmed=FALSE,
                last_factor_counter=-1,factor_attempts=0,factor_window_started=NULL WHERE user_id=?
            """,Timestamp.from(entry.deletedAt()),entry.targetId());
        jdbc.update("UPDATE auth_users SET email=?,password_hash='!deleted',display_name='Deleted account',email_verified=FALSE WHERE id=?",
                "deleted:"+entry.targetId(),entry.targetId());
        // UUID tombstone and existing restricted moderation records remain; no credential/profile payload does.
        return TerminalReapplyStore.Cleanup.PRESERVED;
    }

    void lockOperatorAuthority() {
        jdbc.queryForObject("SELECT id FROM platform_operator_lock WHERE id=1 FOR UPDATE",Integer.class);
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
