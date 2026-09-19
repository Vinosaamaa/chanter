package com.chanter.media.application;

import com.chanter.media.domain.CourseResource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns durable reservations, idempotency, worker leases and deletion accounting. No network calls run in its transactions. */
@Repository
public class ResourceLifecycle {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final long byteLimit;
    private final int requestLimit;
    private final int cleanupReserve;
    private final com.chanter.common.events.SearchEventWriter searchEvents;

    public ResourceLifecycle(JdbcClient jdbc, Clock clock,
            @Value("${chanter.media.byte-limit:8000000000}") long byteLimit,
            @Value("${chanter.media.request-limit:40000}") int requestLimit,
            @Value("${chanter.media.cleanup-request-reserve:4000}") int cleanupReserve,
            com.chanter.common.events.SearchEventWriter searchEvents) {
        if (byteLimit < 1 || byteLimit > 8_000_000_000L || requestLimit < 1 || requestLimit > 40_000
                || cleanupReserve < 1 || cleanupReserve >= requestLimit) throw new IllegalArgumentException("Invalid free storage budget");
        this.jdbc = jdbc; this.clock = clock; this.byteLimit = byteLimit; this.requestLimit = requestLimit; this.cleanupReserve = cleanupReserve;
        this.searchEvents = searchEvents;
    }

    @Transactional
    public void bindNamespace(String identity) {
        String digest;
        try {
            digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        var binding = jdbc.sql("SELECT storage_namespace FROM media_storage_budget WHERE id=1 FOR UPDATE")
                .query((rs, row) -> new Namespace(rs.getString("storage_namespace"))).single();
        if (binding.digest() == null) {
            jdbc.sql("UPDATE media_storage_budget SET storage_namespace=:digest WHERE id=1").param("digest", digest).update();
        } else if (!binding.digest().equals(digest)) {
            throw new IllegalStateException("Private storage namespace changed; reviewed data migration is required");
        }
    }
    private record Namespace(String digest) {}

    @Transactional
    public CourseResource reserve(CourseResource r) {
        long used = jdbc.sql("SELECT reserved_bytes FROM media_storage_budget WHERE id=1 FOR UPDATE").query(Long.class).single();
        Optional<CourseResource> existing = jdbc.sql("SELECT * FROM course_resources WHERE uploaded_by_user_id=:user AND idempotency_key=:key")
                .param("user", r.uploadedByUserId()).param("key", r.idempotencyKey()).query(ResourceLifecycle::map).optional();
        if (existing.isPresent()) {
            CourseResource old = existing.get();
            if (!old.courseId().equals(r.courseId()) || !old.title().equals(r.title()) || !old.fileName().equals(r.fileName())
                    || !old.contentType().equals(r.contentType()) || old.aiApproved() != r.aiApproved()
                    || old.byteSize() != r.byteSize() || !old.sha256().equals(r.sha256())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different upload");
            }
            return old;
        }
        if (r.byteSize() < 1 || r.byteSize() > byteLimit - used) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Course Resource storage quota reached");
        jdbc.sql("""
                INSERT INTO course_resources (id,course_id,title,file_name,content_type,byte_size,storage_key,ai_approved,
                  uploaded_by_user_id,created_at,state,sha256,idempotency_key,storage_backend,updated_at)
                VALUES (:id,:course,:title,:file,:type,:bytes,:object,:ai,:user,:created,'STAGING',:hash,:key,:backend,:created)
                """).param("id", r.id()).param("course", r.courseId()).param("title", r.title()).param("file", r.fileName())
                .param("type", r.contentType()).param("bytes", r.byteSize()).param("object", r.storageKey()).param("ai", r.aiApproved())
                .param("user", r.uploadedByUserId()).param("created", time(r.createdAt())).param("hash", r.sha256())
                .param("key", r.idempotencyKey()).param("backend", r.storageBackend()).update();
        jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=reserved_bytes+:bytes WHERE id=1").param("bytes", r.byteSize()).update();
        return r;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void countRequest(boolean maintenance) {
        var budget = jdbc.sql("SELECT * FROM media_storage_budget WHERE id=1 FOR UPDATE").query((rs, n) ->
                new Budget(rs.getString("request_month"), rs.getInt("foreground_requests"), rs.getInt("maintenance_requests"))).single();
        String month = YearMonth.now(clock).toString();
        int foreground = budget.month().equals(month) ? budget.foreground() : 0;
        int background = budget.month().equals(month) ? budget.maintenance() : 0;
        if (foreground + background >= requestLimit || (!maintenance && foreground >= requestLimit - cleanupReserve)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private storage request budget reached");
        }
        jdbc.sql("UPDATE media_storage_budget SET request_month=:month, foreground_requests=:foreground, maintenance_requests=:maintenance WHERE id=1")
                .param("month", month).param("foreground", foreground + (maintenance ? 0 : 1))
                .param("maintenance", background + (maintenance ? 1 : 0)).update();
    }

    public Optional<CourseResource> find(UUID id) {
        return jdbc.sql("SELECT * FROM course_resources WHERE id=:id").param("id", id).query(ResourceLifecycle::map).optional();
    }

    public List<CourseResource> list(UUID course, boolean instructor) {
        return jdbc.sql("SELECT * FROM course_resources WHERE course_id=:course AND "
                        + (instructor ? "state NOT IN ('DELETE_PENDING','DELETED')" : "state='AVAILABLE'") + " ORDER BY created_at,id")
                .param("course", course).query(ResourceLifecycle::map).list();
    }

    @Transactional
    public void quarantine(UUID id) {
        jdbc.sql("UPDATE course_resources SET state='QUARANTINED', updated_at=:now WHERE id=:id AND state='STAGING'")
                .param("now", now()).param("id", id).update();
    }

    @Transactional
    public void requestDelete(UUID id) {
        int changed = jdbc.sql("UPDATE course_resources SET state='DELETE_PENDING', updated_at=:now, retry_at=NULL WHERE id=:id AND state NOT IN ('DELETED','DELETE_PENDING')")
                .param("now", now()).param("id", id).update();
        if (changed == 1) publishSearch(id, true);
    }

    @Transactional
    public Optional<Job> claim(boolean migrateLegacy) {
        Instant instant = clock.instant();
        var row = jdbc.sql("""
                SELECT * FROM course_resources WHERE
                  (lease_until IS NULL OR lease_until<:now) AND (retry_at IS NULL OR retry_at<=:now)
                  AND (storage_backend<>'legacy' OR :migrate=TRUE OR state='DELETE_PENDING') AND (
                    state IN ('QUARANTINED','SCANNING','DELETE_PENDING')
                    OR (state='SCAN_FAILED' AND byte_reservation=TRUE AND (attempts<5 OR (updated_at<:expired AND storage_backend<>'legacy')))
                    OR (state='REJECTED' AND byte_reservation=TRUE)
                    OR (state='AVAILABLE' AND ingestion_status IN ('PENDING','FAILED'))
                    OR (state='STAGING' AND created_at<:abandoned)
                    OR (state='LEGACY' AND :migrate=TRUE))
                ORDER BY updated_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """).param("now", time(instant)).param("expired", time(instant.minusSeconds(86400)))
                .param("abandoned", time(instant.minusSeconds(600))).param("migrate", migrateLegacy)
                .query((rs, n) -> new Candidate(map(rs, n), rs.getInt("attempts"), rs.getString("migration_key"))).optional();
        if (row.isEmpty()) return Optional.empty();
        CourseResource r = row.get().resource();
        boolean delete = List.of("DELETE_PENDING", "REJECTED", "STAGING").contains(r.state())
                || (r.state().equals("SCAN_FAILED") && row.get().attempts() >= 5);
        boolean index = r.state().equals("AVAILABLE");
        String state = delete ? (r.state().equals("STAGING") ? "DELETE_PENDING" : r.state()) : index ? "AVAILABLE" : "SCANNING";
        UUID lease = UUID.randomUUID();
        String migrationKey = row.get().migrationKey();
        if (!delete && r.storageBackend().equals("legacy") && migrationKey == null) migrationKey = PrivateResourceStorage.PREFIX + r.courseId() + "/" + r.id() + "/" + UUID.randomUUID();
        jdbc.sql("UPDATE course_resources SET state=:state, lease_id=:lease, lease_until=:until, migration_key=:migration, attempts=attempts+1, updated_at=:now WHERE id=:id")
                .param("state", state).param("lease", lease).param("until", time(instant.plusSeconds(180)))
                .param("migration", migrationKey).param("now", time(instant)).param("id", r.id()).update();
        return Optional.of(new Job(r, lease, delete ? "DELETE" : index ? "INDEX" : r.storageBackend().equals("legacy") ? "MIGRATE" : "SCAN", migrationKey));
    }

    @Transactional
    public void finishMigration(Job job, UploadValidator.ValidatedUpload upload, String backend) {
        int changed = jdbc.sql("""
                UPDATE course_resources SET storage_key=:key, storage_backend=:backend, sha256=:hash,
                 file_name=:file,content_type=:type,state='QUARANTINED',ingestion_status='NONE',lease_id=NULL,lease_until=NULL,
                 attempts=0,retry_at=NULL,updated_at=:now WHERE id=:id AND lease_id=:lease AND state='SCANNING'
                """).param("key", job.migrationKey()).param("backend", backend).param("hash", upload.sha256())
                .param("file", upload.fileName()).param("type", upload.contentType()).param("now", now())
                .param("id", job.resource().id()).param("lease", job.leaseId()).update();
        if (changed == 0) releaseLease(job.resource().id(), job.leaseId());
    }

    @Transactional
    public boolean finishScan(UUID id, UUID lease, String state) {
        if (!List.of("AVAILABLE", "REJECTED", "SCAN_FAILED").contains(state)) throw new IllegalArgumentException("Invalid scan result");
        int changed = jdbc.sql("""
                UPDATE course_resources SET state=:state, lease_id=NULL, lease_until=NULL, updated_at=:now,
                  ingestion_status=CASE WHEN :state='AVAILABLE' AND ai_approved THEN 'PENDING' ELSE 'NONE' END,
                  attempts=CASE WHEN :state='AVAILABLE' THEN 0 ELSE attempts END,
                  retry_at=:retry WHERE id=:id AND lease_id=:lease AND state='SCANNING'
                """).param("state", state).param("now", now())
                .param("retry", state.equals("SCAN_FAILED") ? time(clock.instant().plusSeconds(60)) : null)
                .param("id", id).param("lease", lease).update();
        if (changed == 0) releaseLease(id, lease);
        if (changed == 1 && state.equals("AVAILABLE")) publishSearch(id, false);
        return changed == 1;
    }

    @Transactional
    public void finishIndex(UUID id, UUID lease, boolean complete) {
        int changed = jdbc.sql("""
                UPDATE course_resources SET ingestion_status=:status,lease_id=NULL,lease_until=NULL,
                  retry_at=:retry,updated_at=:now WHERE id=:id AND lease_id=:lease AND state='AVAILABLE'
                """).param("status", complete ? "COMPLETE" : "FAILED")
                .param("retry", complete ? null : time(clock.instant().plusSeconds(600)))
                .param("now", now()).param("id", id).param("lease", lease).update();
        if (changed == 0) releaseLease(id, lease);
    }

    @Transactional
    public void finishDelete(UUID id, UUID lease) {
        // All quota mutations take this row first, preventing inversion with upload reservations.
        jdbc.sql("SELECT reserved_bytes FROM media_storage_budget WHERE id=1 FOR UPDATE").query(Long.class).single();
        var row = jdbc.sql("SELECT * FROM course_resources WHERE id=:id AND lease_id=:lease FOR UPDATE")
                .param("id", id).param("lease", lease).query((rs, n) -> new Deleted(rs.getLong("byte_size"), rs.getBoolean("byte_reservation"))).optional();
        if (row.isEmpty()) return;
        jdbc.sql("""
                UPDATE course_resources SET state=CASE WHEN state='DELETE_PENDING' THEN 'DELETED' ELSE state END,
                 byte_reservation=FALSE, lease_id=NULL,lease_until=NULL,retry_at=NULL,updated_at=:now WHERE id=:id
                """).param("now", now()).param("id", id).update();
        if (row.get().reserved()) jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=reserved_bytes-:bytes WHERE id=1")
                .param("bytes", row.get().bytes()).update();
    }

    @Transactional
    public void retryJob(UUID id, UUID lease) {
        jdbc.sql("UPDATE course_resources SET lease_id=NULL,lease_until=NULL,retry_at=:retry WHERE id=:id AND lease_id=:lease")
                .param("retry", time(clock.instant().plusSeconds(60))).param("id", id).param("lease", lease).update();
    }

    private void releaseLease(UUID id, UUID lease) {
        jdbc.sql("UPDATE course_resources SET lease_id=NULL,lease_until=NULL WHERE id=:id AND lease_id=:lease")
                .param("id", id).param("lease", lease).update();
    }

    public Usage courseUsage(UUID course) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN byte_reservation THEN byte_size ELSE 0 END),0) AS reserved,
                  COALESCE(SUM(CASE WHEN state='AVAILABLE' THEN byte_size ELSE 0 END),0) AS available
                FROM course_resources WHERE course_id=:course
                """).param("course", course).query((rs, n) -> new Usage(rs.getLong("reserved"), rs.getLong("available"))).single();
    }

    public boolean orphaned(String key) {
        return jdbc.sql("SELECT COUNT(*) FROM course_resources WHERE (storage_key=:key OR migration_key=:key) AND byte_reservation=TRUE")
                .param("key", key).query(Integer.class).single() == 0;
    }

    private OffsetDateTime now() { return time(clock.instant()); }
    private void publishSearch(UUID id, boolean deleted) {
        CourseResource resource = find(id).orElseThrow();
        searchEvents.append(new com.chanter.common.events.SearchChange("RESOURCE", id, null, resource.courseId(),
                null, null, null, resource.title(), resource.fileName(), null, deleted));
    }
    private static OffsetDateTime time(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private static CourseResource map(ResultSet rs, int row) throws SQLException {
        return new CourseResource(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("title"),
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("byte_size"), rs.getString("storage_key"),
                rs.getBoolean("ai_approved"), rs.getObject("uploaded_by_user_id", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("state"), rs.getString("sha256"), rs.getObject("idempotency_key", UUID.class), rs.getString("storage_backend"));
    }
    private record Budget(String month, int foreground, int maintenance) { }
    private record Candidate(CourseResource resource, int attempts, String migrationKey) { }
    private record Deleted(long bytes, boolean reserved) { }
    public record Job(CourseResource resource, UUID leaseId, String operation, String migrationKey) { }
    public record Usage(long reservedBytes, long availableBytes) { }
}
