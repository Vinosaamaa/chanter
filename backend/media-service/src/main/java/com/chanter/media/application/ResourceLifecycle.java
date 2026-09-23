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
    private final boolean recovery;
    private final com.chanter.common.events.SearchEventWriter searchEvents;
    private final com.chanter.common.events.ResourceEventWriter resourceEvents;
    private final org.springframework.beans.factory.ObjectProvider<com.chanter.common.lifecycle.TerminalReapplyStore> terminal;

    public ResourceLifecycle(JdbcClient jdbc, Clock clock,
            @Value("${chanter.media.byte-limit:8000000000}") long byteLimit,
            @Value("${chanter.media.request-limit:40000}") int requestLimit,
            @Value("${chanter.media.cleanup-request-reserve:4000}") int cleanupReserve,
            @Value("${chanter.recovery-mode:false}") boolean recovery,
            com.chanter.common.events.SearchEventWriter searchEvents,
            com.chanter.common.events.ResourceEventWriter resourceEvents,
            org.springframework.beans.factory.ObjectProvider<com.chanter.common.lifecycle.TerminalReapplyStore> terminal) {
        if (byteLimit < 1 || byteLimit > 8_000_000_000L || requestLimit < 1 || requestLimit > 40_000
                || cleanupReserve < 1 || cleanupReserve >= requestLimit) throw new IllegalArgumentException("Invalid free storage budget");
        this.jdbc = jdbc; this.clock = clock; this.byteLimit = byteLimit; this.requestLimit = requestLimit; this.cleanupReserve = cleanupReserve;
        this.recovery=recovery;
        this.searchEvents = searchEvents;
        this.resourceEvents = resourceEvents;
        this.terminal=terminal;
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
            // Restored absence is unknown authority, never evidence for a newly supplied object namespace.
            if(recovery) return;
            jdbc.sql("UPDATE media_storage_budget SET storage_namespace=:digest WHERE id=1").param("digest", digest).update();
        } else if (!binding.digest().equals(digest)) {
            throw new IllegalStateException("Private storage namespace changed; reviewed data migration is required");
        }
    }
    private record Namespace(String digest) {}

    @Transactional
    public CourseResource reserve(CourseResource r) {
        lockTerminal(); requireWritable(r);
        long used = jdbc.sql("SELECT reserved_bytes FROM media_storage_budget WHERE id=1 FOR UPDATE").query(Long.class).single();
        Optional<CourseResource> existing = jdbc.sql("SELECT * FROM course_resources WHERE uploaded_by_user_id=:user AND idempotency_key=:key")
                .param("user", r.uploadedByUserId()).param("key", r.idempotencyKey()).query(ResourceLifecycle::map).optional();
        if (existing.isPresent()) {
            CourseResource old = existing.get();
            requireWritable(old);
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
                  uploaded_by_user_id,created_at,state,sha256,idempotency_key,storage_backend,updated_at,study_server_id,storage_write_settled)
                VALUES (:id,:course,:title,:file,:type,:bytes,:object,:ai,:user,:created,'STAGING',:hash,:key,:backend,:created,:server,FALSE)
                """).param("id", r.id()).param("course", r.courseId()).param("title", r.title()).param("file", r.fileName())
                .param("type", r.contentType()).param("bytes", r.byteSize()).param("object", r.storageKey()).param("ai", r.aiApproved())
                .param("user", r.uploadedByUserId()).param("created", time(r.createdAt())).param("hash", r.sha256())
                .param("key", r.idempotencyKey()).param("backend", r.storageBackend()).param("server", r.studyServerId()).update();
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
        storageWriteSettled(id);
        jdbc.sql("UPDATE course_resources SET state='QUARANTINED', updated_at=:now WHERE id=:id AND state='STAGING'")
                .param("now", now()).param("id", id).update();
    }

    /** Only an owning completed invocation or a typed definitive adapter failure may settle an active write. */
    @Transactional
    public void storageWriteSettled(UUID id) {
        jdbc.sql("UPDATE course_resources SET storage_write_settled=TRUE WHERE id=:id")
                .param("id",id).update();
    }

    @Transactional
    public void migrationWriteSettled(UUID id,UUID lease) {
        jdbc.sql("UPDATE course_resources SET storage_write_settled=TRUE WHERE id=:id AND lease_id=:lease")
                .param("id",id).param("lease",lease).update();
    }

    @Transactional
    public boolean beginMigrationWrite(UUID id,UUID lease) {
        return jdbc.sql("UPDATE course_resources SET storage_write_settled=FALSE WHERE id=:id AND lease_id=:lease AND state='SCANNING' AND storage_write_settled=TRUE")
                .param("id",id).param("lease",lease).update()==1;
    }

    @Transactional
    public void requestDelete(UUID id) {
        lockTerminal();
        int changed = jdbc.sql("UPDATE course_resources SET state='DELETE_PENDING', updated_at=:now, retry_at=NULL WHERE id=:id AND state NOT IN ('DELETED','DELETE_PENDING')")
                .param("now", now()).param("id", id).update();
        if (changed == 1) {
            publishSearch(id, true);
            resourceEvents.append(new com.chanter.common.events.ResourceChanged(id, null, null, null, null, false, true));
        }
    }

    @Transactional
    public Optional<Job> claim(boolean migrateLegacy) {
        lockTerminal();
        queueTerminalResources();
        Instant instant = clock.instant();
        // An abandoned unacknowledged upload remains unavailable, with its byte reservation and uncertainty intact.
        jdbc.sql("UPDATE course_resources SET state='DELETE_PENDING',updated_at=:now WHERE id IN (SELECT id FROM course_resources WHERE state='STAGING' AND storage_write_settled=FALSE AND created_at<:abandoned ORDER BY created_at,id LIMIT 256)")
                .param("now",time(instant)).param("abandoned",time(instant.minusSeconds(600))).update();
        var row = jdbc.sql("""
                SELECT * FROM course_resources WHERE
                  storage_write_settled=TRUE AND
                  (lease_until IS NULL OR lease_until<:now) AND (retry_at IS NULL OR retry_at<=:now)
                  AND (storage_backend<>'legacy' OR :migrate=TRUE OR state='DELETE_PENDING') AND (
                    state IN ('QUARANTINED','SCANNING','DELETE_PENDING')
                    OR (state='SCAN_FAILED' AND byte_reservation=TRUE AND (attempts<5 OR (updated_at<:expired AND storage_backend<>'legacy')))
                    OR (state='REJECTED' AND byte_reservation=TRUE)
                    OR (state='AVAILABLE' AND ingestion_status IN ('PENDING','PROCESSING'))
                    OR (state='STAGING' AND created_at<:abandoned)
                    OR (state='LEGACY' AND :migrate=TRUE))
                ORDER BY updated_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """).param("now", time(instant)).param("expired", time(instant.minusSeconds(86400)))
                .param("abandoned", time(instant.minusSeconds(600))).param("migrate", migrateLegacy)
                .query((rs, n) -> new Candidate(map(rs, n), rs.getInt("attempts"), rs.getString("migration_key"))).optional();
        if (row.isEmpty()) return Optional.empty();
        CourseResource r = row.get().resource();
        if(terminalScope(r) && !List.of("DELETE_PENDING","DELETED").contains(r.state())) {
            requestDelete(r.id()); r=find(r.id()).orElseThrow();
        }
        boolean delete = List.of("DELETE_PENDING", "REJECTED", "STAGING").contains(r.state())
                || (r.state().equals("SCAN_FAILED") && row.get().attempts() >= 5);
        boolean index = r.state().equals("AVAILABLE");
        String state = delete ? (r.state().equals("STAGING") ? "DELETE_PENDING" : r.state()) : index ? "AVAILABLE" : "SCANNING";
        UUID lease = UUID.randomUUID();
        String migrationKey = row.get().migrationKey();
        if (!delete && r.storageBackend().equals("legacy") && migrationKey == null) migrationKey = PrivateResourceStorage.PREFIX + r.courseId() + "/" + r.id() + "/" + UUID.randomUUID();
        jdbc.sql("UPDATE course_resources SET state=:state, ingestion_status=CASE WHEN :index THEN 'PROCESSING' ELSE ingestion_status END, lease_id=:lease, lease_until=:until, migration_key=:migration, attempts=attempts+1, updated_at=:now WHERE id=:id")
                .param("index", index)
                .param("state", state).param("lease", lease).param("until", time(instant.plusSeconds(180)))
                .param("migration", migrationKey).param("now", time(instant)).param("id", r.id()).update();
        return Optional.of(new Job(r, lease, delete ? "DELETE" : index ? "INDEX" : r.storageBackend().equals("legacy") ? "MIGRATE" : "SCAN", migrationKey));
    }

    @Transactional
    public void finishMigration(Job job, UploadValidator.ValidatedUpload upload, String backend) {
        lockTerminal();
        migrationWriteSettled(job.resource().id(),job.leaseId());
        if(terminalScope(job.resource())) { requestDelete(job.resource().id()); releaseLease(job.resource().id(),job.leaseId()); return; }
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
        lockTerminal();
        var resource=find(id);
        if(resource.isPresent() && terminalScope(resource.get())) { requestDelete(id); releaseLease(id,lease); return false; }
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
        if (changed == 1 && state.equals("AVAILABLE")) {
            publishSearch(id, false);
            if (find(id).orElseThrow().studyServerId() != null) publishIngestion(id);
        }
        return changed == 1;
    }

    @Transactional
    public void finishIndex(UUID id, UUID lease, boolean complete) {
        finishIndex(id, lease, new ResourceIngestionClient.Outcome(complete ? "READY" : "FAILED", java.util.Set.of()));
    }

    @Transactional
    public void finishIndex(UUID id, UUID lease, ResourceIngestionClient.Outcome outcome) {
        int changed = jdbc.sql("""
                UPDATE course_resources SET ingestion_status=:status,ingestion_signals=:signals,lease_id=NULL,lease_until=NULL,
                  retry_at=:retry,updated_at=:now WHERE id=:id AND lease_id=:lease AND state='AVAILABLE'
                """).param("status", outcome.status())
                .param("signals", outcome.signals().stream().sorted().collect(java.util.stream.Collectors.joining(",")))
                .param("retry", java.util.Set.of("PENDING", "PROCESSING").contains(outcome.status()) ? time(clock.instant().plusSeconds(5)) : null)
                .param("now", now()).param("id", id).param("lease", lease).update();
        if (changed == 0) releaseLease(id, lease);
    }

    @Transactional
    public void retryIndex(UUID id) {
        lockTerminal(); find(id).ifPresent(this::requireWritable);
        int changed = jdbc.sql("""
                UPDATE course_resources SET ingestion_status='PENDING',ingestion_signals='',retry_at=NULL,updated_at=:now
                WHERE id=:id AND state='AVAILABLE' AND ai_approved=TRUE AND ingestion_status='FAILED'
                    AND (lease_until IS NULL OR lease_until<:now)
                """).param("id", id).param("now", now()).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed AI preparation can be retried");
        if (find(id).orElseThrow().studyServerId() != null) publishIngestion(id);
        else jdbc.sql("UPDATE course_resources SET ingestion_event_id=NULL WHERE id=:id").param("id", id).update();
    }

    @Transactional
    public void setAiApproved(UUID id, boolean approved) {
        lockTerminal();
        var current = jdbc.sql("SELECT * FROM course_resources WHERE id=:id FOR UPDATE").param("id", id)
                .query(ResourceLifecycle::map).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource not found"));
        requireWritable(current);
        if (!current.state().equals("AVAILABLE")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Course Resource is not available");
        if (current.aiApproved() == approved) return;
        jdbc.sql("""
                UPDATE course_resources SET ai_approved=:approved,ingestion_status=:status,ingestion_signals='',
                    ingestion_event_id=NULL,lease_id=NULL,lease_until=NULL,retry_at=NULL,updated_at=:now WHERE id=:id
                """).param("approved", approved).param("status", approved || current.studyServerId() == null ? "PENDING" : "NONE")
                .param("now", now()).param("id", id).update();
        if (current.studyServerId() != null) publishIngestion(id);
        publishSearch(id, false);
    }

    @Transactional
    public void queueLegacyIndex(UUID id, UUID lease, UUID server) {
        if (server == null) throw new IllegalArgumentException("Course scope is required");
        lockTerminal();
        if(terminalTarget("STUDY_SERVER",server)) { requestDelete(id); releaseLease(id,lease); return; }
        var row = jdbc.sql("SELECT * FROM course_resources WHERE id=:id AND lease_id=:lease FOR UPDATE")
                .param("id", id).param("lease", lease).query(ResourceLifecycle::map).optional();
        if(row.isPresent() && terminalScope(row.get())) { requestDelete(id); releaseLease(id,lease); return; }
        if (row.isPresent() && row.get().state().equals("AVAILABLE") && row.get().ingestionEventId() == null) {
            jdbc.sql("UPDATE course_resources SET study_server_id=:server WHERE id=:id").param("server", server).param("id", id).update();
            publishIngestion(id);
        }
        releaseLease(id, lease);
    }

    public boolean ingestionDeliveryFailed(UUID event) {
        return jdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE id=:id AND destination='agent' AND status='FAILED'")
                .param("id", event).query(Integer.class).single() == 1;
    }

    @Transactional
    public void synchronizeIndex(UUID id, UUID lease, UUID event, ResourceIngestionClient.Outcome outcome) {
        var current = jdbc.sql("SELECT * FROM course_resources WHERE id=:id FOR UPDATE").param("id", id)
                .query(ResourceLifecycle::map).optional();
        if (current.isPresent() && event.equals(current.get().ingestionEventId())) finishIndex(id, lease, outcome);
        else releaseLease(id, lease);
    }

    @Transactional
    public void finishDelete(UUID id, UUID lease) {
        lockTerminal();
        // All quota mutations take this row first, preventing inversion with upload reservations.
        jdbc.sql("SELECT reserved_bytes FROM media_storage_budget WHERE id=1 FOR UPDATE").query(Long.class).single();
        var row = jdbc.sql("SELECT * FROM course_resources WHERE id=:id AND lease_id=:lease AND storage_write_settled=TRUE FOR UPDATE")
                .param("id", id).param("lease", lease).query((rs, n) -> new Deleted(rs.getLong("byte_size"), rs.getBoolean("byte_reservation"))).optional();
        if (row.isEmpty()) return;
        jdbc.sql("""
                UPDATE course_resources SET state=CASE WHEN state='DELETE_PENDING' THEN 'DELETED' ELSE state END,
                 byte_reservation=FALSE, lease_id=NULL,lease_until=NULL,retry_at=NULL,updated_at=:now WHERE id=:id
                """).param("now", now()).param("id", id).update();
        if (row.get().reserved()) jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=reserved_bytes-:bytes WHERE id=1")
                .param("bytes", row.get().bytes()).update();
    }

    public record MaintenanceDeletion(UUID resourceId,String storageBackend,String currentKey,String migrationKey,long byteSize,String sha256) { }

    /** The owning maintenance transaction has already verified every physical closure receipt against current inventory. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void finishVerifiedMaintenanceDelete(MaintenanceDeletion proof) {
        if(proof==null || proof.resourceId()==null || proof.storageBackend()==null || proof.currentKey()==null
                || proof.byteSize()<0 || proof.sha256()==null || !proof.sha256().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid maintenance deletion tuple");
        lockTerminal();
        jdbc.sql("SELECT reserved_bytes FROM media_storage_budget WHERE id=1 FOR UPDATE").query(Long.class).single();
        var row=jdbc.sql("SELECT * FROM course_resources WHERE id=:id FOR UPDATE").param("id",proof.resourceId())
                .query((rs,n) -> new MaintenanceRow(map(rs,n),rs.getString("migration_key"),rs.getObject("lease_id",UUID.class),
                        rs.getBoolean("storage_write_settled"),rs.getBoolean("byte_reservation"))).optional()
                .orElseThrow(() -> new IllegalStateException("Maintenance resource is absent"));
        var resource=row.resource();
        if(!row.settled() || row.lease()!=null || !terminalScope(resource)
                || !List.of("DELETE_PENDING","DELETED").contains(resource.state())
                || !proof.storageBackend().equals(resource.storageBackend()) || !proof.currentKey().equals(resource.storageKey())
                || !java.util.Objects.equals(proof.migrationKey(),row.migration()) || proof.byteSize()!=resource.byteSize()
                || !proof.sha256().equals(resource.sha256())) throw new IllegalStateException("Maintenance deletion no longer matches settled terminal source");
        if(resource.state().equals("DELETED")) {
            if(row.reserved()) throw new IllegalStateException("Deleted resource still has a reservation");
        } else {
            jdbc.sql("UPDATE course_resources SET state='DELETED',byte_reservation=FALSE,retry_at=NULL,updated_at=:now WHERE id=:id")
                    .param("now",now()).param("id",resource.id()).update();
            if(row.reserved() && jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=reserved_bytes-:bytes WHERE id=1 AND reserved_bytes>=:bytes")
                    .param("bytes",resource.byteSize()).update()!=1) throw new IllegalStateException("Storage reservation changed");
        }
        reconcileTerminalResource(resource);
    }
    private void reconcileTerminalResource(CourseResource resource) {
        var store=terminal.getObject();
        store.reconcile("RESOURCE",resource.id());store.reconcile("ACCOUNT",resource.uploadedByUserId());
        var servers=new java.util.HashSet<UUID>();if(resource.studyServerId()!=null) servers.add(resource.studyServerId());
        for(String table:List.of("lifecycle_scope_import","lifecycle_recovery_scope")) {
            servers.addAll(jdbc.sql(("""
                    SELECT DISTINCT s.study_server_id FROM lifecycle_scope_import_ids s JOIN lifecycle_scope_imports h
                        ON h.study_server_id=s.study_server_id AND h.scope_kind=s.scope_kind
                    JOIN lifecycle_terminal_targets t ON t.target_kind='STUDY_SERVER' AND t.target_id=h.study_server_id
                        AND t.revision=h.revision AND t.event_id=h.event_id AND t.digest=h.terminal_digest
                    WHERE h.ready=TRUE AND s.scope_kind='COURSE' AND s.scope_id=:course
                    """).replace("lifecycle_scope_import",table)).param("course",resource.courseId()).query(UUID.class).list());
        }
        for(UUID server:servers) store.reconcile("STUDY_SERVER",server);
    }
    private record MaintenanceRow(CourseResource resource,String migration,UUID lease,boolean settled,boolean reserved) { }

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

    /** Current source access is closed even while physical deletion or a later cleanup page is pending. */
    public boolean terminalScope(CourseResource resource) {
        if(terminalTarget("RESOURCE",resource.id()) || terminalTarget("ACCOUNT",resource.uploadedByUserId())
                || terminalTarget("STUDY_SERVER",resource.studyServerId())) return true;
        for(String table:List.of("lifecycle_scope_import","lifecycle_recovery_scope")) {
            if(jdbc.sql(scopePredicate(table,"s.scope_id=:course")).param("course",resource.courseId()).query(Boolean.class).single()) return true;
        }
        return false;
    }

    private boolean terminalTarget(String kind,UUID id) {
        return id!=null && jdbc.sql("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind=:kind AND target_id=:id")
                .param("kind",kind).param("id",id).query(Integer.class).single()!=0;
    }
    private void requireWritable(CourseResource resource) {
        if(terminalScope(resource)) throw new ResponseStatusException(HttpStatus.GONE,"LIFECYCLE_TARGET_DELETED");
    }
    private void lockTerminal() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Media terminal mutation requires transaction");
        jdbc.sql("SELECT revision FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE").query(Long.class).single();
    }
    private static String scopePredicate(String table,String match) {
        return ("""
                SELECT EXISTS(SELECT 1 FROM lifecycle_scope_import_ids s JOIN lifecycle_scope_imports h
                    ON h.study_server_id=s.study_server_id AND h.scope_kind=s.scope_kind
                    JOIN lifecycle_terminal_targets t ON t.target_kind='STUDY_SERVER' AND t.target_id=h.study_server_id
                        AND t.revision=h.revision AND t.event_id=h.event_id AND t.digest=h.terminal_digest
                    WHERE h.ready=TRUE AND s.scope_kind='COURSE' AND %s)
                """).formatted(match).replace("lifecycle_scope_import",table);
    }
    private void queueTerminalResources() {
        String scope=scopePredicate("lifecycle_scope_import","s.scope_id=r.course_id").substring("SELECT ".length());
        String historical=scopePredicate("lifecycle_recovery_scope","s.scope_id=r.course_id").substring("SELECT ".length());
        var ids=jdbc.sql("""
                SELECT r.id FROM course_resources r WHERE state NOT IN ('DELETE_PENDING','DELETED') AND (
                    EXISTS(SELECT 1 FROM lifecycle_terminal_targets t WHERE (t.target_kind='RESOURCE' AND t.target_id=r.id)
                        OR (t.target_kind='ACCOUNT' AND t.target_id=r.uploaded_by_user_id)
                        OR (t.target_kind='STUDY_SERVER' AND t.target_id=r.study_server_id))
                    OR %s OR %s) ORDER BY r.id LIMIT 256
                """.formatted(scope,historical)).query(UUID.class).list();
        ids.forEach(this::requestDelete);
    }

    /** First bounded page inside terminal/reconciliation transaction; the existing worker handles later pages. */
    @Transactional
    public void queueTerminalDeletion(com.chanter.common.lifecycle.TerminalJournal.Entry entry,boolean scoped,String scopeTable) {
        lockTerminal(); entry.validate();
        if(!List.of("lifecycle_scope_import_ids","lifecycle_recovery_scope_ids").contains(scopeTable)) throw new IllegalArgumentException("Invalid source scope");
        String match=switch(entry.targetKind()) {
            case "ACCOUNT" -> "uploaded_by_user_id=:target";
            case "RESOURCE" -> "id=:target";
            case "STUDY_SERVER" -> "(study_server_id=:target"+(scoped ? " OR course_id IN (SELECT scope_id FROM "+scopeTable+" WHERE study_server_id=:target AND scope_kind='COURSE')" : "")+")";
            default -> throw new IllegalArgumentException("Unknown terminal target");
        };
        jdbc.sql("SELECT id FROM course_resources WHERE state NOT IN ('DELETE_PENDING','DELETED') AND "+match+" ORDER BY id LIMIT 256")
                .param("target",entry.targetId()).query(UUID.class).list().forEach(this::requestDelete);
    }

    private OffsetDateTime now() { return time(clock.instant()); }
    private void publishSearch(UUID id, boolean deleted) {
        CourseResource resource = find(id).orElseThrow();
        searchEvents.append(new com.chanter.common.events.SearchChange("RESOURCE", id, null, resource.courseId(),
                null, null, null, resource.title(), resource.fileName(), null, deleted));
    }
    private void publishIngestion(UUID id) {
        var resource = find(id).orElseThrow();
        UUID event = resourceEvents.append(new com.chanter.common.events.ResourceChanged(id, resource.courseId(),
                resource.studyServerId(), resource.sha256(), resource.fileName(), resource.aiApproved(), false));
        jdbc.sql("UPDATE course_resources SET ingestion_event_id=:event,ingestion_status=:status,ingestion_signals='',retry_at=NULL WHERE id=:id")
                .param("event", event).param("status", resource.aiApproved() ? "PENDING" : "NONE").param("id", id).update();
    }
    private static OffsetDateTime time(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private static CourseResource map(ResultSet rs, int row) throws SQLException {
        return new CourseResource(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("title"),
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("byte_size"), rs.getString("storage_key"),
                rs.getBoolean("ai_approved"), rs.getObject("uploaded_by_user_id", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("state"), rs.getString("sha256"), rs.getObject("idempotency_key", UUID.class), rs.getString("storage_backend"),
                rs.getString("ingestion_status"), rs.getString("ingestion_signals").isBlank() ? java.util.Set.of() : java.util.Set.of(rs.getString("ingestion_signals").split(",")),
                rs.getObject("study_server_id", UUID.class), rs.getObject("ingestion_event_id", UUID.class));
    }
    private record Budget(String month, int foreground, int maintenance) { }
    private record Candidate(CourseResource resource, int attempts, String migrationKey) { }
    private record Deleted(long bytes, boolean reserved) { }
    public record Job(CourseResource resource, UUID leaseId, String operation, String migrationKey) { }
    public record Usage(long reservedBytes, long availableBytes) { }
}
