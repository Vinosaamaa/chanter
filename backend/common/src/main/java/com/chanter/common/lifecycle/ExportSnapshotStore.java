package com.chanter.common.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Source-owned immutable export pages. No request can supply a query, storage key, or archive path. */
public final class ExportSnapshotStore {
    public static final int PAGE_BYTES = 256 * 1024;
    public static final long MAX_SNAPSHOT_BYTES = 256L * 1024 * 1024;
    public static final long MAX_RETAINED_BYTES = 1024L * 1024 * 1024;
    public static final Duration RETENTION = Duration.ofHours(24);
    public static final Duration CAPTURE_BUDGET = Duration.ofMinutes(5);
    public static final String SCHEMA = """
        CREATE TABLE data_export_lock (id INT PRIMARY KEY);
        INSERT INTO data_export_lock VALUES (1);
        CREATE TABLE data_export_account_tombstones (account_id UUID PRIMARY KEY, deleted_at TIMESTAMP WITH TIME ZONE NOT NULL);
        CREATE TABLE data_export_snapshots (
            id UUID PRIMARY KEY, account_id UUID NOT NULL, requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
            captured_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
            status VARCHAR(16) NOT NULL, byte_size BIGINT NOT NULL DEFAULT 0
        );
        CREATE INDEX data_export_expiry ON data_export_snapshots(expires_at);
        CREATE TABLE data_export_entries (
            snapshot_id UUID NOT NULL REFERENCES data_export_snapshots(id) ON DELETE CASCADE,
            ordinal INT NOT NULL, entry_path VARCHAR(120) NOT NULL, media_type VARCHAR(100) NOT NULL,
            byte_size BIGINT NOT NULL DEFAULT 0, page_count INT NOT NULL DEFAULT 0, sha256 VARCHAR(64),
            PRIMARY KEY(snapshot_id, ordinal), UNIQUE(snapshot_id, entry_path)
        );
        CREATE TABLE data_export_entry_scopes (
            snapshot_id UUID NOT NULL, entry_ordinal INT NOT NULL, access_kind VARCHAR(24) NOT NULL, access_id UUID NOT NULL,
            expected_digest VARCHAR(64),
            PRIMARY KEY(snapshot_id,entry_ordinal,access_kind,access_id),
            FOREIGN KEY(snapshot_id,entry_ordinal) REFERENCES data_export_entries(snapshot_id,ordinal) ON DELETE CASCADE
        );
        CREATE TABLE data_export_pages (
            snapshot_id UUID NOT NULL, entry_ordinal INT NOT NULL, ordinal INT NOT NULL, payload BYTEA NOT NULL,
            PRIMARY KEY(snapshot_id, entry_ordinal, ordinal),
            FOREIGN KEY(snapshot_id, entry_ordinal) REFERENCES data_export_entries(snapshot_id, ordinal) ON DELETE CASCADE
        )
        """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String source;

    public ExportSnapshotStore(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper mapper, Clock clock, String source) {
        if (!List.of("auth", "community", "message", "media", "agent", "notification", "search").contains(source))
            throw new IllegalArgumentException("Unknown export source");
        this.jdbc = jdbc; this.tx = tx; this.mapper = mapper; this.clock = clock; this.source = source;
    }

    /** Joins the consumer transaction, so the snapshot and its receipt commit or roll back together. */
    public Manifest capture(Request request, Projection projection) {
        request.validate(clock.instant());
        return tx.execute(status -> {
            lock();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM data_export_account_tombstones WHERE account_id=?", Integer.class, request.accountId()) > 0)
                throw failure(HttpStatus.GONE, "EXPORT_ACCOUNT_DELETED");
            var existing = headers(request.jobId());
            if (!existing.isEmpty()) {
                Header header = existing.getFirst();
                if (!header.accountId().equals(request.accountId()) || !header.requestedAt().equals(request.requestedAt())
                        || !header.expiresAt().equals(request.expiresAt())) throw failure(HttpStatus.CONFLICT, "EXPORT_SCOPE_MISMATCH");
                return manifest(request.jobId(), request.accountId());
            }
            expireLocked();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots WHERE account_id=? AND status='READY'", Integer.class, request.accountId()) > 0)
                throw failure(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_ALREADY_AVAILABLE");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots WHERE status='READY'", Integer.class) >= 16)
                throw failure(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_CAPACITY");
            long retained = jdbc.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM data_export_snapshots", Long.class);
            jdbc.update("""
                INSERT INTO data_export_snapshots(id,account_id,requested_at,captured_at,expires_at,status)
                VALUES (?,?,?,?,?,'BUILDING')
                """, request.jobId(), request.accountId(), time(request.requestedAt()), time(clock.instant()), time(request.expiresAt()));
            var writer = new Capture(request.jobId(), retained);
            try { projection.write(writer); }
            catch (IOException failure) { throw new ExportFailure("EXPORT_SOURCE_UNAVAILABLE", failure); }
            writer.checkBudget(); request.validate(clock.instant());
            jdbc.update("UPDATE data_export_snapshots SET status='READY',byte_size=? WHERE id=?", writer.totalBytes, request.jobId());
            return manifest(request.jobId(), request.accountId());
        });
    }

    public Manifest manifest(UUID jobId, UUID accountId) {
        Header header = requireReadable(jobId, accountId);
        List<Entry> entries = jdbc.query("""
            SELECT ordinal,entry_path,media_type,byte_size,page_count,sha256 FROM data_export_entries
            WHERE snapshot_id=? ORDER BY ordinal
            """, (rs, row) -> new Entry(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getInt(5), rs.getString(6)), jobId);
        return new Manifest(1, source, jobId, accountId, header.capturedAt(), header.expiresAt(), List.copyOf(entries));
    }

    public byte[] page(UUID jobId, UUID accountId, int entry, int page) {
        if (entry < 0 || entry >= 10_000 || page < 0 || page >= MAX_SNAPSHOT_BYTES / PAGE_BYTES)
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_EXPORT_PAGE");
        requireReadable(jobId, accountId);
        List<byte[]> rows = jdbc.query("SELECT payload FROM data_export_pages WHERE snapshot_id=? AND entry_ordinal=? AND ordinal=?",
                (rs, row) -> rs.getBytes(1), jobId, entry, page);
        if (rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "EXPORT_PAGE_NOT_FOUND");
        byte[] result = rows.getFirst();
        if (result.length > PAGE_BYTES) throw new ExportFailure("EXPORT_INTEGRITY_FAILURE", null);
        return result;
    }

    /** Private source controllers use this around every retained read; a receipt is not current content authorization. */
    public void requireAccess(UUID jobId, UUID accountId, Integer entry, ExportSnapshotAccess access) {
        requireReadable(jobId, accountId);
        String sql = "SELECT DISTINCT access_kind,access_id,expected_digest FROM data_export_entry_scopes WHERE snapshot_id=?";
        Object[] parameters = entry == null ? new Object[]{jobId} : new Object[]{jobId, entry};
        if (entry != null) sql += " AND entry_ordinal=?";
        var scopes = jdbc.query(sql, (rs, row) -> new AccessScope(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3)), parameters);
        for (int start = 0; start < scopes.size(); start += 100)
            access.requireAll(accountId, scopes.subList(start, Math.min(start + 100, scopes.size())));
    }

    /** Preserve cancellation authority until the expired request cannot be replayed. */
    public void cancelAccount(UUID accountId) {
        tx.executeWithoutResult(status -> {
            lock();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM data_export_account_tombstones WHERE account_id=?", Integer.class, accountId) == 0)
                jdbc.update("INSERT INTO data_export_account_tombstones VALUES (?,?)", accountId, time(clock.instant()));
            jdbc.update("DELETE FROM data_export_entries WHERE snapshot_id IN (SELECT id FROM data_export_snapshots WHERE account_id=?)", accountId);
            jdbc.update("UPDATE data_export_snapshots SET status='CANCELLED',byte_size=0 WHERE account_id=?", accountId);
        });
    }

    /** Cancels one export without deleting the account; rejects a delayed capture with the same job ID. */
    public void cancelJob(Request request) {
        request.validate(clock.instant());
        tx.executeWithoutResult(status -> {
            lock();
            var existing = headers(request.jobId());
            if (!existing.isEmpty() && (!existing.getFirst().accountId().equals(request.accountId())
                    || !existing.getFirst().requestedAt().equals(request.requestedAt()) || !existing.getFirst().expiresAt().equals(request.expiresAt())))
                throw failure(HttpStatus.CONFLICT, "EXPORT_SCOPE_MISMATCH");
            if (existing.isEmpty()) jdbc.update("""
                INSERT INTO data_export_snapshots(id,account_id,requested_at,captured_at,expires_at,status)
                VALUES (?,?,?,?,?,'CANCELLED')
                """, request.jobId(), request.accountId(), time(request.requestedAt()), time(clock.instant()), time(request.expiresAt()));
            jdbc.update("DELETE FROM data_export_entries WHERE snapshot_id=?", request.jobId());
            jdbc.update("UPDATE data_export_snapshots SET status='CANCELLED',byte_size=0 WHERE id=?", request.jobId());
        });
    }

    public void expire() { tx.executeWithoutResult(status -> { lock(); expireLocked(); }); }

    private void expireLocked() {
        jdbc.update("DELETE FROM data_export_snapshots WHERE expires_at<=?", time(clock.instant()));
    }
    private void lock() { jdbc.queryForObject("SELECT id FROM data_export_lock WHERE id=1 FOR UPDATE", Integer.class); }
    private List<Header> headers(UUID jobId) {
        return jdbc.query("SELECT account_id,requested_at,captured_at,expires_at,status FROM data_export_snapshots WHERE id=?",
                (rs, row) -> new Header(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant(), rs.getString(5)), jobId);
    }
    private Header requireReadable(UUID jobId, UUID accountId) {
        List<Header> rows = headers(jobId);
        if (rows.isEmpty() || !rows.getFirst().accountId().equals(accountId)) throw failure(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND");
        Header header = rows.getFirst();
        if (!"READY".equals(header.status()) || !header.expiresAt().isAfter(clock.instant()))
            throw failure(HttpStatus.GONE, "EXPORT_UNAVAILABLE");
        return header;
    }

    public final class Capture {
        private final UUID jobId;
        private final long retainedBytes;
        private final Instant deadline;
        private int nextEntry;
        private int scopeCount;
        private long totalBytes;
        private Capture(UUID jobId, long retainedBytes) {
            this.jobId = jobId; this.retainedBytes = retainedBytes; this.deadline = clock.instant().plus(CAPTURE_BUDGET);
        }
        public void checkBudget() {
            if (!clock.instant().isBefore(deadline)) throw new ExportFailure("EXPORT_CAPTURE_TIME_LIMIT", null);
        }

        public void jsonLines(String section, JsonProjection projection) throws IOException {
            if (section == null || !section.matches("[a-z][a-z0-9_-]{0,39}")) throw new IllegalArgumentException("Invalid export section");
            jsonLinesAt(section + ".jsonl", List.of(), projection);
        }

        public void protectedJsonLines(String group, UUID entryId, AccessScope access, JsonProjection projection) throws IOException {
            protectedJsonLines(group, entryId, List.of(access), projection);
        }
        public void protectedJsonLines(String group, UUID entryId, List<AccessScope> access, JsonProjection projection) throws IOException {
            if (!java.util.Set.of("conversations", "answers", "notifications").contains(group) || entryId == null || access == null || access.isEmpty() || access.size() > 100)
                throw new IllegalArgumentException("Invalid protected export entry");
            access = List.copyOf(access);
            access.forEach(AccessScope::validate);
            jsonLinesAt(group + "/" + entryId + ".jsonl", access, projection);
        }

        private void jsonLinesAt(String path, List<AccessScope> access, JsonProjection projection) throws IOException {
            try (var output = new PageOutput(path, "application/x-ndjson", access)) {
                projection.write(value -> {
                    byte[] bytes = mapper.writeValueAsBytes(value);
                    if (bytes.length > PAGE_BYTES) throw new ExportFailure("EXPORT_RECORD_TOO_LARGE", null);
                    output.write(bytes); output.write('\n');
                });
            }
        }

        public void file(UUID resourceId, InputStream input) throws IOException {
            if (resourceId == null || input == null) throw new IllegalArgumentException("Invalid export file");
            try (var output = new PageOutput("files/" + resourceId + "/content", "application/octet-stream", List.of(new AccessScope("RESOURCE", resourceId)))) {
                byte[] buffer = new byte[PAGE_BYTES];
                int count;
                while ((count = input.read(buffer)) != -1) if (count > 0) output.write(buffer, 0, count);
            }
        }

        private final class PageOutput extends OutputStream {
            private final int ordinal;
            private final MessageDigest digest = sha256();
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(PAGE_BYTES);
            private long size;
            private int pages;
            private boolean closed;
            PageOutput(String entryPath, String mediaType, List<AccessScope> access) {
                checkBudget();
                if (nextEntry >= 10_000) throw new ExportFailure("EXPORT_ENTRY_LIMIT", null);
                if (scopeCount + access.size() > 10_000) throw new ExportFailure("EXPORT_PROTECTED_ITEM_LIMIT", null);
                scopeCount += access.size();
                ordinal = nextEntry++;
                jdbc.update("INSERT INTO data_export_entries(snapshot_id,ordinal,entry_path,media_type) VALUES (?,?,?,?)", jobId, ordinal, entryPath, mediaType);
                for (AccessScope scope : access) jdbc.update("INSERT INTO data_export_entry_scopes VALUES (?,?,?,?,?)", jobId, ordinal, scope.kind(), scope.id(), scope.expectedDigest());
            }
            @Override public void write(int value) { write(new byte[]{(byte) value}, 0, 1); }
            @Override public void write(byte[] bytes, int offset, int length) {
                checkBudget();
                java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
                if (closed) throw new IllegalStateException("Closed export entry");
                if (length > MAX_SNAPSHOT_BYTES - totalBytes || length > MAX_RETAINED_BYTES - retainedBytes - totalBytes)
                    throw new ExportFailure("EXPORT_SIZE_LIMIT", null);
                digest.update(bytes, offset, length);
                size += length; totalBytes += length;
                while (length > 0) {
                    int count = Math.min(length, PAGE_BYTES - buffer.size());
                    buffer.write(bytes, offset, count); offset += count; length -= count;
                    if (buffer.size() == PAGE_BYTES) flushPage();
                }
            }
            private void flushPage() {
                jdbc.update("INSERT INTO data_export_pages(snapshot_id,entry_ordinal,ordinal,payload) VALUES (?,?,?,?)", jobId, ordinal, pages++, buffer.toByteArray());
                buffer.reset();
            }
            @Override public void close() {
                if (closed) return;
                closed = true;
                if (buffer.size() > 0) flushPage();
                String hash = HexFormat.of().formatHex(digest.digest());
                jdbc.update("UPDATE data_export_entries SET byte_size=?,page_count=?,sha256=? WHERE snapshot_id=? AND ordinal=?", size, pages, hash, jobId, ordinal);
                jdbc.update("UPDATE data_export_entry_scopes SET expected_digest=? WHERE snapshot_id=? AND entry_ordinal=? AND access_kind='RESOURCE' AND expected_digest IS NULL",
                        hash, jobId, ordinal);
            }
        }
    }

    public record Request(UUID jobId, UUID accountId, Instant requestedAt, Instant expiresAt) {
        public Request {
            if (requestedAt != null) requestedAt = requestedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            if (expiresAt != null) expiresAt = expiresAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        }
        public void validate(Instant now) {
            if (jobId == null || accountId == null || requestedAt == null || expiresAt == null
                    || requestedAt.isAfter(now.plusSeconds(5)) || !expiresAt.isAfter(requestedAt)
                    || expiresAt.isAfter(requestedAt.plus(RETENTION))) throw failure(HttpStatus.BAD_REQUEST, "INVALID_EXPORT_REQUEST");
            if (!expiresAt.isAfter(now)) throw failure(HttpStatus.GONE, "EXPORT_EXPIRED");
        }
    }
    public record Entry(int ordinal, String path, String mediaType, long bytes, int pageCount, String sha256) { }
    public record AccessScope(String kind, UUID id, String expectedDigest) {
        public AccessScope(String kind, UUID id) { this(kind, id, null); }
        public AccessScope { validate(kind, id, expectedDigest); }
        public void validate() { validate(kind, id, expectedDigest); }
        private static void validate(String kind, UUID id, String expectedDigest) {
            if (kind == null || !java.util.Set.of("DM_PEER", "DM_MESSAGE", "RESOURCE", "AI_ANSWER", "NOTIFICATION").contains(kind) || id == null)
                throw new IllegalArgumentException("Invalid export access scope");
            if (expectedDigest != null && !expectedDigest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid export scope digest");
        }
    }
    public record Manifest(int schemaVersion, String source, UUID jobId, UUID accountId, Instant capturedAt, Instant expiresAt, List<Entry> entries) {
        public Manifest { entries = List.copyOf(entries); }

        /** Receipt fingerprint covers immutable metadata; each entry separately covers its exact bytes. */
        public String fingerprint() {
            if (schemaVersion != 1 || source == null || !List.of("auth", "community", "message", "media", "agent", "notification", "search").contains(source)
                    || jobId == null || accountId == null || capturedAt == null || expiresAt == null || !expiresAt.isAfter(capturedAt()) || entries.size() > 10_000)
                throw new IllegalArgumentException("Invalid export manifest");
            var digest = sha256();
            update(digest, "1\n" + source + "\n" + jobId + "\n" + accountId + "\n" + capturedAt + "\n" + expiresAt + "\n");
            long bytes = 0;
            var paths = new java.util.HashSet<String>();
            for (int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                boolean json = entry.path() != null && (entry.path().matches("[a-z][a-z0-9_-]{0,39}\\.jsonl")
                        || entry.path().matches("(conversations|answers|notifications)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jsonl"));
                boolean file = entry.path() != null && entry.path().matches("files/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/content");
                if (entry.ordinal() != index || (!json && !file) || !paths.add(entry.path()) || entry.bytes() < 0
                        || entry.bytes() > MAX_SNAPSHOT_BYTES - bytes || entry.pageCount() != (entry.bytes() + PAGE_BYTES - 1) / PAGE_BYTES
                        || !(json ? "application/x-ndjson" : "application/octet-stream").equals(entry.mediaType())
                        || entry.sha256() == null || !entry.sha256().matches("[a-f0-9]{64}"))
                    throw new IllegalArgumentException("Invalid export entry");
                bytes += entry.bytes();
                update(digest, index + "\n" + entry.path() + "\n" + entry.mediaType() + "\n" + entry.bytes() + "\n" + entry.pageCount() + "\n" + entry.sha256() + "\n");
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        private static void update(MessageDigest digest, String value) { digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private record Header(UUID accountId, Instant requestedAt, Instant capturedAt, Instant expiresAt, String status) { }
    @FunctionalInterface public interface Projection { void write(Capture capture) throws IOException; }
    @FunctionalInterface public interface JsonProjection { void write(JsonRows rows) throws IOException; }
    @FunctionalInterface public interface JsonRows { void add(Object row) throws IOException; }
    public static final class ExportFailure extends RuntimeException {
        public ExportFailure(String code, Throwable cause) { super(code, cause); }
    }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private static ResponseStatusException failure(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
