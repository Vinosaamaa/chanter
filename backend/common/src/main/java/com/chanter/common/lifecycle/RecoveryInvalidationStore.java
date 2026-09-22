package com.chanter.common.lifecycle;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The owning recovery transaction locks and verifies authority before invalidating credentials. */
public final class RecoveryInvalidationStore {
    public static final String SCHEMA = """
        CREATE TABLE lifecycle_recovery_invalidations (
            recovery_id UUID PRIMARY KEY, revision BIGINT NOT NULL, digest VARCHAR(64) NOT NULL,
            invalidated_at TIMESTAMP WITH TIME ZONE NOT NULL
        )
        """;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String source;
    private final String scope;

    public RecoveryInvalidationStore(JdbcTemplate jdbc, Clock clock, String source) {
        this.jdbc = jdbc; this.clock = clock; this.source = source;
        scope = switch (source) {
            case "auth" -> "ALL_BROWSER_SESSIONS";
            case "agent" -> "ALL_PENDING_NATIVE_REQUESTS";
            default -> throw new IllegalArgumentException("Unsupported invalidation source");
        };
    }

    public Receipt invalidate(Request request, TerminalJournal.Watermark lockedAuthority, java.util.function.Consumer<Instant> mutation) {
        request.validate();
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Invalidation requires the owning authority transaction");
        if (!request.authority().equals(lockedAuthority)) throw new IllegalArgumentException("Recovery authority does not match applied prefix");
        var existing = jdbc.query("SELECT revision,digest,invalidated_at FROM lifecycle_recovery_invalidations WHERE recovery_id=?",
                (rs, row) -> new Receipt(1, source, request.recoveryId(), new TerminalJournal.Watermark(rs.getLong(1), rs.getString(2)),
                        rs.getTimestamp(3).toInstant(), scope), request.recoveryId());
        if (!existing.isEmpty()) {
            if (!existing.getFirst().authority().equals(request.authority())) throw new IllegalArgumentException("Recovery identity changed authority");
            return existing.getFirst();
        }
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        mutation.accept(now);
        jdbc.update("INSERT INTO lifecycle_recovery_invalidations VALUES (?,?,?,?)", request.recoveryId(), request.authority().revision(),
                request.authority().digest(), Timestamp.from(now));
        return new Receipt(1, source, request.recoveryId(), request.authority(), now, scope);
    }

    public record Request(UUID recoveryId, TerminalJournal.Watermark authority) {
        public void validate() {
            if (recoveryId == null || recoveryId.equals(new UUID(0, 0)) || authority == null)
                throw new IllegalArgumentException("Invalid recovery identity");
            authority.validate();
        }
    }
    public record Receipt(int schemaVersion, String source, UUID recoveryId, TerminalJournal.Watermark authority,
                          Instant invalidatedAt, String scope) { }
}
