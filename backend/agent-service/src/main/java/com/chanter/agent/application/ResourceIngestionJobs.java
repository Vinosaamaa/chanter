package com.chanter.agent.application;

import com.chanter.common.events.DurableEvent;
import com.chanter.common.events.ResourceChanged;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One durable extraction job per resource; generation and terminal deletion govern every completion. */
@Repository
public class ResourceIngestionJobs {
    private final JdbcClient jdbc;
    private final ResourceChunkRepository chunks;
    private final Clock clock;
    public ResourceIngestionJobs(JdbcClient jdbc, ResourceChunkRepository chunks, Clock clock) {
        this.jdbc = jdbc; this.chunks = chunks; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(DurableEvent event, ResourceChanged change) {
        change.validate();
        jdbc.sql("INSERT INTO resource_index_lifecycle(resource_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", change.resourceId()).update();
        var previous = jdbc.sql("SELECT * FROM resource_index_lifecycle WHERE resource_id=:id FOR UPDATE")
                .param("id", change.resourceId()).query((rs, row) -> new Previous(rs.getBoolean("deleted"),
                        rs.getObject("course_id", UUID.class), rs.getObject("study_server_id", UUID.class),
                        rs.getString("source_sha256"), rs.getString("file_name"), rs.getString("parser_version"), rs.getString("status"))).single();
        // A synchronous deletion may precede the durable terminal event. Old live deliveries must still acknowledge safely.
        if (previous.deleted()) return;
        if (change.deleted()) { chunks.deleteByResourceId(change.resourceId()); return; }
        if (previous.course() != null && !previous.course().equals(change.courseId())
                || previous.server() != null && !previous.server().equals(change.studyServerId())) {
            throw new IllegalArgumentException("Resource scope cannot change");
        }
        boolean reuse = change.aiApproved() && "READY".equals(previous.status())
                && change.sourceSha256().equals(previous.sha()) && change.fileName().equals(previous.file())
                && ResourceTextExtractor.PARSER_VERSION.equals(previous.parser());
        jdbc.sql("""
                UPDATE resource_index_lifecycle SET course_id=:course,study_server_id=:server,source_sha256=:sha,
                    file_name=:file,parser_version=:parser,source_event_id=:event,source_revision=:revision,
                    generation=generation+:advance,status=:status,signals=CASE WHEN :reuse THEN signals ELSE '' END,
                    job_attempts=0,job_lease_id=NULL,job_lease_until=NULL,job_retry_at=NULL WHERE resource_id=:id
                """).param("course", change.courseId()).param("server", change.studyServerId())
                .param("sha", change.sourceSha256()).param("file", change.fileName()).param("parser", ResourceTextExtractor.PARSER_VERSION)
                .param("event", event.id()).param("revision", event.revision()).param("advance", reuse ? 0 : 1)
                .param("status", reuse ? "READY" : change.aiApproved() ? "PENDING" : "NONE")
                .param("reuse", reuse).param("id", change.resourceId()).update();
        if (!reuse) chunks.replaceAllForResource(change.resourceId(), List.of());
    }

    @Transactional
    public Optional<Job> claim() {
        var now = now();
        jdbc.sql("""
                UPDATE resource_index_lifecycle SET generation=generation+1,status='FAILED',job_lease_id=NULL,job_lease_until=NULL
                WHERE deleted=FALSE AND source_event_id IS NOT NULL AND status='PROCESSING'
                    AND job_lease_until<=:now AND job_attempts>=5
                """).param("now", now).update();
        var candidate = jdbc.sql("""
                SELECT * FROM resource_index_lifecycle WHERE deleted=FALSE AND source_event_id IS NOT NULL
                    AND status IN ('PENDING','PROCESSING') AND job_attempts<5
                    AND (job_lease_until IS NULL OR job_lease_until<=:now)
                    AND (job_retry_at IS NULL OR job_retry_at<=:now)
                ORDER BY source_revision,resource_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """).param("now", now).query((rs, row) -> new Job(rs.getObject("resource_id", UUID.class),
                        rs.getObject("course_id", UUID.class), rs.getObject("study_server_id", UUID.class),
                        rs.getObject("source_event_id", UUID.class), rs.getLong("source_revision"), rs.getString("source_sha256"),
                        rs.getString("file_name"), rs.getLong("generation") + 1, UUID.randomUUID())).optional();
        if (candidate.isEmpty()) return Optional.empty();
        var job = candidate.get();
        jdbc.sql("""
                UPDATE resource_index_lifecycle SET generation=:generation,status='PROCESSING',job_attempts=job_attempts+1,
                    job_lease_id=:lease,job_lease_until=:until,job_retry_at=NULL WHERE resource_id=:id
                """).param("generation", job.generation()).param("lease", job.leaseId()).param("until", now.plusMinutes(10))
                .param("id", job.resourceId()).update();
        chunks.replaceAllForResource(job.resourceId(), List.of());
        return Optional.of(job);
    }

    @Transactional
    public void failed(Job job) {
        jdbc.sql("""
                UPDATE resource_index_lifecycle SET status=CASE WHEN job_attempts>=5 THEN 'FAILED' ELSE 'PENDING' END,
                    job_lease_id=NULL,job_lease_until=NULL,job_retry_at=:retry
                WHERE resource_id=:id AND generation=:generation AND source_event_id=:event
                    AND job_lease_id=:lease AND deleted=FALSE AND status IN ('PROCESSING','FAILED')
                """).param("retry", now().plusSeconds(30)).param("id", job.resourceId()).param("generation", job.generation())
                .param("event", job.eventId()).param("lease", job.leaseId()).update();
    }

    public State status(UUID resourceId, UUID eventId) {
        return jdbc.sql("SELECT status,source_sha256,signals FROM resource_index_lifecycle WHERE resource_id=:id AND source_event_id=:event AND deleted=FALSE")
                .param("id", resourceId).param("event", eventId).query((rs, row) -> new State(resourceId, eventId,
                        rs.getString("source_sha256"), rs.getString("status"), rs.getString("signals").isBlank()
                        ? Set.of() : Set.of(rs.getString("signals").split(",")))).optional()
                .orElse(new State(resourceId, eventId, null, "PENDING", Set.of()));
    }
    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }
    private record Previous(boolean deleted, UUID course, UUID server, String sha, String file, String parser, String status) {}
    public record Job(UUID resourceId, UUID courseId, UUID studyServerId, UUID eventId, long sourceRevision,
            String sourceSha256, String fileName, long generation, UUID leaseId) {}
    public record State(UUID resourceId, UUID eventId, String sourceSha256, String status, Set<String> signals) {}
}
