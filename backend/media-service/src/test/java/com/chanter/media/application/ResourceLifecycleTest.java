package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;

import com.chanter.media.domain.CourseResource;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ResourceLifecycleTest {
    @Autowired ResourceLifecycle lifecycle;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;

    @org.junit.jupiter.api.BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM course_resources").update();
        jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=0, foreground_requests=0, maintenance_requests=0").update();
    }

    private CourseResource candidate(UUID key) {
        UUID id = UUID.randomUUID();
        return new CourseResource(id, UUID.randomUUID(), "Notes", "notes.txt", "text/plain", 10,
                "resources/v1/" + UUID.randomUUID() + "/" + id + "/" + UUID.randomUUID(), false,
                UUID.randomUUID(), Instant.now(), "STAGING", "a".repeat(64), key, "local");
    }

    @Test
    void concurrentIdempotentReservationChargesBytesOnlyOnceAndRejectsConflictingPayload() {
        var resource = candidate(UUID.randomUUID());
        var results = IntStream.range(0, 8).mapToObj(i -> CompletableFuture.supplyAsync(() -> lifecycle.reserve(resource))).toList();
        assertThat(results.stream().map(CompletableFuture::join)).allMatch(r -> r.id().equals(resource.id()));
        assertThat(lifecycle.courseUsage(resource.courseId()).reservedBytes()).isEqualTo(10);
        var conflict = new CourseResource(UUID.randomUUID(), resource.courseId(), "Different", resource.fileName(), resource.contentType(),
                resource.byteSize(), resource.storageKey(), false, resource.uploadedByUserId(), resource.createdAt(), "STAGING",
                resource.sha256(), resource.idempotencyKey(), "local");
        assertThatThrownBy(() -> lifecycle.reserve(conflict)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void deletedResourceCannotBePublishedByAnInFlightScannerAndQuotaReleasesOnlyAfterConfirmedDelete() {
        var resource = lifecycle.reserve(candidate(UUID.randomUUID()));
        lifecycle.quarantine(resource.id());
        var job = lifecycle.claim(false).orElseThrow();
        lifecycle.requestDelete(resource.id());
        assertThat(lifecycle.finishScan(resource.id(), job.leaseId(), "AVAILABLE")).isFalse();
        assertThat(lifecycle.courseUsage(resource.courseId()).reservedBytes()).isEqualTo(10);
        var deletion = lifecycle.claim(false).orElseThrow();
        lifecycle.finishDelete(resource.id(), deletion.leaseId());
        lifecycle.finishDelete(resource.id(), deletion.leaseId());
        assertThat(lifecycle.courseUsage(resource.courseId()).reservedBytes()).isZero();
    }

    @Test
    void concurrentRequestsNeverExceedBudgetAndCleanupKeepsItsReserve() {
        jdbc.sql("UPDATE media_storage_budget SET request_month=:month,foreground_requests=35999")
                .param("month", java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString()).update();
        var accepted = new java.util.concurrent.atomic.AtomicInteger();
        var calls = IntStream.range(0, 8).mapToObj(i -> CompletableFuture.runAsync(() -> {
            try { lifecycle.countRequest(false); accepted.incrementAndGet(); }
            catch (org.springframework.web.server.ResponseStatusException limited) { assertThat(limited.getStatusCode().value()).isEqualTo(503); }
        })).toList();
        calls.forEach(CompletableFuture::join);
        assertThat(accepted.get()).isEqualTo(1);
        lifecycle.countRequest(true);
        assertThat(jdbc.sql("SELECT maintenance_requests FROM media_storage_budget WHERE id=1").query(Integer.class).single()).isEqualTo(1);
        jdbc.sql("UPDATE media_storage_budget SET maintenance_requests=4000").update();
        assertThatThrownBy(() -> lifecycle.countRequest(true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void staleLeaseCannotPublishAndCleanedFailureIsNotRepeatedlyClaimed() {
        var resource = lifecycle.reserve(candidate(UUID.randomUUID())); lifecycle.quarantine(resource.id());
        var old = lifecycle.claim(false).orElseThrow();
        jdbc.sql("UPDATE course_resources SET lease_until=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        var current = lifecycle.claim(false).orElseThrow();
        assertThat(lifecycle.finishScan(resource.id(), old.leaseId(), "AVAILABLE")).isFalse();
        lifecycle.finishScan(resource.id(), current.leaseId(), "SCAN_FAILED");
        jdbc.sql("UPDATE course_resources SET attempts=5,retry_at=NULL,updated_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        var cleanup = lifecycle.claim(false).orElseThrow(); assertThat(cleanup.operation()).isEqualTo("DELETE");
        lifecycle.finishDelete(resource.id(), cleanup.leaseId());
        jdbc.sql("UPDATE course_resources SET updated_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        assertThat(lifecycle.claim(false)).isEmpty();
        assertThat(lifecycle.reserve(resource).publicStatus()).isEqualTo("FAILED");
    }

    @Test
    void onlyAvailableResourcesPublishAndDeletionDominatesLateScan() {
        var resource = lifecycle.reserve(candidate(UUID.randomUUID()));
        lifecycle.quarantine(resource.id());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key=:key")
                .param("key", "RESOURCE:" + resource.id()).query(Integer.class).single()).isZero();
        var scan = lifecycle.claim(false).orElseThrow();
        assertThat(lifecycle.finishScan(resource.id(), scan.leaseId(), "AVAILABLE")).isTrue();
        lifecycle.requestDelete(resource.id());
        assertThat(lifecycle.finishScan(resource.id(), scan.leaseId(), "AVAILABLE")).isFalse();
        var payloads = jdbc.sql("SELECT payload FROM durable_outbox WHERE aggregate_key=:key AND destination='search' ORDER BY revision")
                .param("key", "RESOURCE:" + resource.id()).query(String.class).list();
        assertThat(payloads).hasSize(2);
        assertThat(payloads.get(0)).contains("\"deleted\":false");
        assertThat(payloads.get(1)).contains("\"deleted\":true");
    }
}
