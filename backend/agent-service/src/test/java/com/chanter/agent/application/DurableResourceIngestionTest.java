package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "chanter.ingestion.worker-enabled=false")
@ActiveProfiles("test")
class DurableResourceIngestionTest {
    @Autowired ResourceIngestionJobs jobs;
    @Autowired ResourceIngestionService ingestion;
    @Autowired ResourceChunkRepository chunks;
    @Autowired JdbcClient jdbc;
    @Autowired JdbcTemplate template;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;

    @Test void duplicateAndSameSourceEventsReuseReadyChunksAndTerminalDeletionWins() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] bytes = "Durable approved evidence".getBytes(StandardCharsets.UTF_8);
        var change = change(resource, course, server, bytes, true);
        var first = event(change, 1);
        assertThat(receive(first, change)).isTrue();
        var claim = jobs.claim().orElseThrow();
        ingestion.ingestClaim(claim, bytes);
        var ids = chunks.findByResourceId(resource).stream().map(c -> c.id()).toList();
        var scope = chunks.findByResourceId(resource).getFirst().sourceScope();
        assertThat(scope.studyServerId()).isEqualTo(server);
        assertThat(scope.cohortId()).isNull();
        assertThat(scope.language()).isEqualTo("und");
        assertThat(scope.accessScope()).isEqualTo("COURSE");
        assertThat(scope.sourceRevision()).isEqualTo(1);
        assertThat(receive(first, change)).isFalse();
        var replay = event(change, 2);
        assertThat(receive(replay, change)).isTrue();
        assertThat(jobs.claim()).isEmpty();
        assertThat(chunks.findByResourceId(resource)).extracting(c -> c.id()).containsExactlyElementsOf(ids);
        assertThat(jobs.status(resource, replay.id()).status()).isEqualTo("READY");
        var deleted = new ResourceChanged(resource, null, null, null, null, false, true);
        assertThat(receive(event(deleted, 3), deleted)).isTrue();
        assertThat(receive(event(change, 4), change)).isFalse();
        assertThat(chunks.findByResourceId(resource)).isEmpty();
        assertThat(jobs.claim()).isEmpty();
        assertThat(jdbc.sql("SELECT study_server_id FROM resource_index_lifecycle WHERE resource_id=:id")
                .param("id", resource).query((rs, row) -> rs.getObject(1)).optional()).isEmpty();
    }

    @Test void supersededClaimsAndWrongBytesCannotPublish() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] oldBytes = "Old evidence".getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = "New evidence".getBytes(StandardCharsets.UTF_8);
        var oldChange = change(resource, course, server, oldBytes, true);
        receive(event(oldChange, 1), oldChange);
        var oldClaim = jobs.claim().orElseThrow();
        var next = change(resource, course, server, newBytes, true);
        receive(event(next, 2), next);
        var newClaim = jobs.claim().orElseThrow();
        assertThatThrownBy(() -> ingestion.ingestClaim(newClaim, oldBytes)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        ingestion.ingestClaim(newClaim, newBytes);
        assertThatThrownBy(() -> ingestion.ingestClaim(oldClaim, oldBytes)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jobs.failed(oldClaim);
        assertThat(chunks.findByResourceId(resource)).extracting(c -> c.contentText()).containsExactly("New evidence");
    }

    private boolean receive(DurableEvent event, ResourceChanged change) {
        return new DurableConsumer(template, new TransactionTemplate(transactions))
                .apply(event, change.deleted(), () -> jobs.accept(event, change));
    }

    @Test void exhaustedLeaseRejectsLateCompletionAndLegacyDirectWrites() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] bytes = "Lease fenced evidence".getBytes(StandardCharsets.UTF_8);
        var change = change(resource, course, server, bytes, true);
        var event = event(change, 1);
        receive(event, change);
        var claim = jobs.claim().orElseThrow();
        assertThatThrownBy(() -> ingestion.ingest(course, resource, "guide.txt", bytes))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.sql("UPDATE resource_index_lifecycle SET job_attempts=5,job_lease_until=:expired WHERE resource_id=:id")
                .param("expired", java.time.OffsetDateTime.now().minusHours(1)).param("id", resource).update();
        assertThat(jobs.claim()).isEmpty();
        assertThatThrownBy(() -> ingestion.ingestClaim(claim, bytes))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jobs.status(resource, event.id()).status()).isEqualTo("FAILED");
        assertThat(chunks.findByResourceId(resource)).isEmpty();
    }

    @Test void workerRetriesTransientFailureFiveTimesAndApprovalRevocationClearsContent() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] bytes = "Approved evidence".getBytes(StandardCharsets.UTF_8);
        var change = change(resource, course, server, bytes, true);
        var event = event(change, 1);
        receive(event, change);
        var failing = new ResourceIngestionWorker(jobs, job -> { throw new IllegalStateException("offline"); }, ingestion, true);
        for (int attempt = 1; attempt <= 5; attempt++) {
            failing.runOnce();
            assertThat(jobs.status(resource, event.id()).status()).isEqualTo(attempt == 5 ? "FAILED" : "PENDING");
            assertThat(jobs.claim()).isEmpty();
            jdbc.sql("UPDATE resource_index_lifecycle SET job_retry_at=NULL WHERE resource_id=:id").param("id", resource).update();
        }
        assertThat(jobs.claim()).isEmpty();
        var retry = event(change, 2);
        receive(retry, change);
        new ResourceIngestionWorker(jobs, job -> bytes, ingestion, true).runOnce();
        assertThat(jobs.status(resource, retry.id()).status()).isEqualTo("READY");
        var revoke = change(resource, course, server, bytes, false);
        var revokedEvent = event(revoke, 3);
        receive(revokedEvent, revoke);
        assertThat(jobs.status(resource, revokedEvent.id()).status()).isEqualTo("NONE");
        assertThat(chunks.findByResourceId(resource)).isEmpty();
        assertThat(jobs.claim()).isEmpty();
    }

    @Test void deletionDuringSourceDownloadRejectsWorkerCompletion() throws Exception {
        UUID resource = UUID.randomUUID(), course = UUID.randomUUID(), server = UUID.randomUUID();
        byte[] bytes = "Delayed source".getBytes(StandardCharsets.UTF_8);
        var change = change(resource, course, server, bytes, true);
        receive(event(change, 1), change);
        new ResourceIngestionWorker(jobs, job -> {
            ingestion.deleteByResourceId(job.resourceId());
            return bytes;
        }, ingestion, true).runOnce();
        assertThat(chunks.findByResourceId(resource)).isEmpty();
        assertThat(jobs.claim()).isEmpty();
    }
    private DurableEvent event(ResourceChanged change, long revision) throws Exception {
        return new DurableEvent(UUID.randomUUID(), 1, "media", revision, "RESOURCE_CHANGED", "RESOURCE:" + change.resourceId(), mapper.writeValueAsString(change));
    }
    private ResourceChanged change(UUID resource, UUID course, UUID server, byte[] bytes, boolean approved) {
        return new ResourceChanged(resource, course, server, ResourceIngestionService.sha256Bytes(bytes), "guide.txt", approved, false);
    }
}
