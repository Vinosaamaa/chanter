package com.chanter.auth.lifecycle;

import com.chanter.common.events.DurableConsumer;
import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.DurableOutbox;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Auth owns readable progress and authorization; source receipts never carry personal export content. */
public final class AccountExportJobs {
    public static final List<String> SOURCES = List.of("auth", "community", "message", "media", "agent", "notification", "search");
    public static final String SCHEMA = """
        CREATE TABLE lifecycle_export_job_lock (id INT PRIMARY KEY);
        INSERT INTO lifecycle_export_job_lock VALUES (1);
        CREATE TABLE lifecycle_export_jobs (
            id UUID PRIMARY KEY, account_id UUID NOT NULL,
            requested_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
            state VARCHAR(16) NOT NULL, cancelled_at TIMESTAMP WITH TIME ZONE
        );
        CREATE INDEX lifecycle_export_jobs_owner ON lifecycle_export_jobs(account_id,requested_at);
        CREATE TABLE lifecycle_export_parts (
            job_id UUID NOT NULL REFERENCES lifecycle_export_jobs(id) ON DELETE CASCADE,
            source VARCHAR(16) NOT NULL, state VARCHAR(16) NOT NULL, fingerprint VARCHAR(64),
            request_event_id UUID, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
            PRIMARY KEY(job_id,source)
        )
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final LifecycleSessionAccess access;
    private final ExportSnapshotStore snapshots;
    private final AuthAccountExport projection;
    private final DurableOutbox outbox;
    private final DurableConsumer consumer;
    private final AccountExportProtocol protocol;
    private final Clock clock;
    public AccountExportJobs(JdbcTemplate jdbc, TransactionTemplate tx, LifecycleSessionAccess access,
            ExportSnapshotStore snapshots, AuthAccountExport projection, DurableOutbox outbox, AccountExportProtocol protocol, Clock clock) {
        this.jdbc = jdbc; this.tx = tx; this.access = access; this.snapshots = snapshots; this.projection = projection;
        this.outbox = outbox; this.consumer = new DurableConsumer(jdbc, tx); this.protocol = protocol; this.clock = clock;
    }

    public Job create(String authorization, UUID requestId) {
        if (requestId == null || requestId.equals(new UUID(0, 0))) throw rejected(HttpStatus.BAD_REQUEST, "INVALID_EXPORT_REQUEST");
        return tx.execute(status -> {
            UUID account = access.require(authorization, true);
            lock();
            var existing = headers(requestId);
            if (!existing.isEmpty()) return read(requestId, account);
            Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_export_jobs WHERE account_id=? AND requested_at>?",
                    Integer.class, account, time(now.minus(java.time.Duration.ofDays(1)))) >= 5)
                throw rejected(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_DAILY_LIMIT");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_export_jobs WHERE account_id=? AND state<>'CANCELLED' AND expires_at>?",
                    Integer.class, account, time(now)) > 0) throw rejected(HttpStatus.CONFLICT, "EXPORT_ALREADY_ACTIVE");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_export_jobs WHERE state<>'CANCELLED' AND expires_at>?",
                    Integer.class, time(now)) >= 16) throw rejected(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_CAPACITY");
            var request = new ExportSnapshotStore.Request(requestId, account, now, now.plus(ExportSnapshotStore.RETENTION));
            jdbc.update("INSERT INTO lifecycle_export_jobs(id,account_id,requested_at,expires_at,state) VALUES (?,?,?,?,'BUILDING')",
                    requestId, account, time(now), time(request.expiresAt()));
            String fingerprint = snapshots.capture(request, output -> projection.capture(account, output)).fingerprint();
            for (String source : SOURCES) {
                UUID event = source.equals("auth") ? null : outbox.append("lifecycle-" + source, AccountExportProtocol.REQUESTED,
                        AccountExportProtocol.key(requestId), protocol.encode(request));
                jdbc.update("INSERT INTO lifecycle_export_parts VALUES (?,?,?,?,?,?)", requestId, source,
                        source.equals("auth") ? "READY" : "PENDING", source.equals("auth") ? fingerprint : null, event, time(now));
            }
            return read(requestId, account);
        });
    }

    public List<Job> list(String authorization) {
        return tx.execute(status -> {
            UUID account = access.require(authorization, false);
            return jdbc.query("SELECT id FROM lifecycle_export_jobs WHERE account_id=? ORDER BY requested_at DESC,id LIMIT 20",
                    (rs, row) -> rs.getObject(1, UUID.class), account).stream().map(id -> read(id, account)).toList();
        });
    }
    public Job get(String authorization, UUID id) {
        return tx.execute(status -> read(id, access.require(authorization, false)));
    }
    public Job cancel(String authorization, UUID id) {
        return tx.execute(status -> {
            UUID account = access.require(authorization, false);
            lock();
            Header header = require(id, account);
            if ("CANCELLED".equals(header.state()) || !header.expiresAt().isAfter(clock.instant())) return read(id, account);
            cancelLocked(id, header);
            return read(id, account);
        });
    }

    /** Owning account closure joins this transaction; user, job, then snapshot locks match public export creation. */
    public void cancelAccount(UUID account) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Account closure requires the owning transaction");
        jdbc.query("SELECT id FROM auth_users WHERE id=? FOR UPDATE", (rs, row) -> rs.getObject(1), account);
        lock();
        var ids = jdbc.query("SELECT id FROM lifecycle_export_jobs WHERE account_id=? AND state<>'CANCELLED' AND expires_at>?",
                (rs, row) -> rs.getObject(1, UUID.class), account, time(clock.instant()));
        for (UUID id : ids) cancelLocked(id, require(id, account));
        snapshots.cancelAccount(account);
    }

    private void cancelLocked(UUID id, Header header) {
            var request = new ExportSnapshotStore.Request(id, header.accountId(), header.requestedAt(), header.expiresAt());
            jdbc.update("UPDATE lifecycle_export_jobs SET state='CANCELLED',cancelled_at=? WHERE id=?", time(clock.instant()), id);
            snapshots.cancelJob(request);
            for (String source : SOURCES) {
                UUID event = source.equals("auth") ? null : outbox.append("lifecycle-" + source, AccountExportProtocol.CANCELLED,
                        AccountExportProtocol.key(id), protocol.encode(request));
                jdbc.update("UPDATE lifecycle_export_parts SET state=?,fingerprint=NULL,request_event_id=?,updated_at=? WHERE job_id=? AND source=?",
                        source.equals("auth") ? "CANCELLED" : "PENDING", event, time(clock.instant()), id, source);
            }
    }

    public void accept(DurableEvent event) {
        var receipt = protocol.receipt(event);
        if ("auth".equals(receipt.source())) throw rejected(HttpStatus.BAD_REQUEST, "INVALID_EXPORT_RECEIPT");
        consumer.apply(event, "CANCELLED".equals(receipt.state()), () -> {
            lock();
            Header job = require(receipt.jobId(), receipt.accountId());
            if (!job.expiresAt().isAfter(clock.instant())) return;
            boolean cancelled = "CANCELLED".equals(job.state());
            if (!cancelled && "CANCELLED".equals(receipt.state())) throw rejected(HttpStatus.CONFLICT, "EXPORT_RECEIPT_STATE_MISMATCH");
            if (cancelled && "READY".equals(receipt.state())) return;
            var current = jdbc.query("SELECT state,fingerprint FROM lifecycle_export_parts WHERE job_id=? AND source=?",
                    (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, receipt.jobId(), receipt.source());
            if (current.size() != 1) throw rejected(HttpStatus.BAD_REQUEST, "INVALID_EXPORT_SOURCE");
            if ("READY".equals(current.getFirst()[0]) && !java.util.Objects.equals(current.getFirst()[1], receipt.fingerprint()))
                throw rejected(HttpStatus.CONFLICT, "EXPORT_RECEIPT_CHANGED");
            jdbc.update("UPDATE lifecycle_export_parts SET state=?,fingerprint=?,updated_at=? WHERE job_id=? AND source=?",
                    receipt.state(), receipt.fingerprint(), time(clock.instant()), receipt.jobId(), receipt.source());
            if (!cancelled && jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_export_parts WHERE job_id=? AND state<>'READY'", Integer.class, receipt.jobId()) == 0)
                jdbc.update("UPDATE lifecycle_export_jobs SET state='READY' WHERE id=?", receipt.jobId());
        });
    }

    /** Rechecked between download pages; cancellation/deletion/logout closes access without a long network transaction. */
    public Job requireDownload(String authorization, UUID id) {
        Job job = get(authorization, id);
        if (!"READY".equals(job.state())) throw rejected(HttpStatus.CONFLICT, "EXPORT_NOT_READY");
        return job;
    }

    public void expireHistory() {
        tx.executeWithoutResult(status -> {
            lock();
            jdbc.update("DELETE FROM lifecycle_export_jobs WHERE expires_at<?", time(clock.instant().minus(java.time.Duration.ofDays(30))));
        });
    }
    private Job read(UUID id, UUID account) {
        Header header = require(id, account);
        boolean expired = !header.expiresAt().isAfter(clock.instant());
        var parts = jdbc.query("""
            SELECT p.source,p.state,p.fingerprint,o.status,o.last_error FROM lifecycle_export_parts p
            LEFT JOIN durable_outbox o ON o.id=p.request_event_id WHERE p.job_id=? ORDER BY p.source
            """, (rs, row) -> new Part(rs.getString(1), rs.getString(2), rs.getString(3),
                    "FAILED".equals(rs.getString(4)) ? "DELIVERY_FAILED" : null), id);
        String state = expired ? "EXPIRED" : header.state();
        boolean cleanupPending = "CANCELLED".equals(state) && parts.stream().anyMatch(part -> !"CANCELLED".equals(part.state()));
        return new Job(1, id, account, header.requestedAt(), header.expiresAt(), state, cleanupPending, List.copyOf(parts));
    }
    private Header require(UUID id, UUID account) {
        var rows = headers(id);
        if (rows.isEmpty() || !rows.getFirst().accountId().equals(account)) throw rejected(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND");
        return rows.getFirst();
    }
    private List<Header> headers(UUID id) {
        return jdbc.query("SELECT account_id,requested_at,expires_at,state FROM lifecycle_export_jobs WHERE id=?",
                (rs, row) -> new Header(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant(), rs.getString(4)), id);
    }
    private void lock() { jdbc.queryForObject("SELECT id FROM lifecycle_export_job_lock WHERE id=1 FOR UPDATE", Integer.class); }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private static ResponseStatusException rejected(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    public record Part(String source, String state, String fingerprint, String errorCode) { }
    public record Job(int schemaVersion, UUID id, UUID accountId, Instant requestedAt, Instant expiresAt, String state,
                      boolean cleanupPending, List<Part> parts) { }
    private record Header(UUID accountId, Instant requestedAt, Instant expiresAt, String state) { }
}
