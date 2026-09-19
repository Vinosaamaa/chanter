package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.chanter.common.events.DurableOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class ExportSnapshotStoreTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ExportSnapshotStore store;
    final Instant now = Instant.parse("2026-09-19T00:00:00Z");
    final UUID owner = UUID.randomUUID();

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(data);
        tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(ExportSnapshotStore.SCHEMA);
        jdbc.execute(DurableOutbox.SCHEMA);
        store = at(now);
    }

    @Test void binaryAndJsonEntriesRemainBoundedAndTheirDigestsMatchTheOriginalContent() throws Exception {
        byte[] original = new byte[ExportSnapshotStore.PAGE_BYTES * 2 + 17];
        new java.util.Random(4).nextBytes(original);
        UUID resource = UUID.randomUUID();
        var request = request();
        var manifest = store.capture(request, writer -> {
            writer.jsonLines("profile", rows -> rows.add(Map.of("displayName", "Ålice", "fileName", "../../private")));
            writer.file(resource, new ByteArrayInputStream(original));
        });
        assertThat(manifest.entries()).extracting(ExportSnapshotStore.Entry::path)
                .containsExactly("profile.jsonl", "files/" + resource + "/content");
        var entry = manifest.entries().get(1);
        assertThat(entry.pageCount()).isEqualTo(3);
        var reconstructed = new ByteArrayOutputStream();
        for (int index = 0; index < entry.pageCount(); index++) {
            byte[] page = store.page(request.jobId(), owner, entry.ordinal(), index);
            assertThat(page.length).isLessThanOrEqualTo(ExportSnapshotStore.PAGE_BYTES);
            reconstructed.write(page);
        }
        assertThat(reconstructed.toByteArray()).isEqualTo(original);
        assertThat(entry.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        assertThat(manifest.fingerprint()).hasSize(64).isEqualTo(store.manifest(request.jobId(), owner).fingerprint());
        assertThat(new String(store.page(request.jobId(), owner, 0, 0), java.nio.charset.StandardCharsets.UTF_8)).contains("Ålice");
    }

    @Test void snapshotAndReceiptRollbackTogetherAndFailedSourceLeavesNoDownloadablePartial() {
        var outbox = new DurableOutbox(jdbc, tx, "community", Clock.fixed(now, ZoneOffset.UTC));
        var request = request();
        assertThatThrownBy(() -> tx.executeWithoutResult(transaction -> {
            store.capture(request, writer -> writer.jsonLines("profile", rows -> rows.add(Map.of("name", "fixture"))));
            outbox.append("notification", "EXPORT_READY", "export:" + request.jobId(), "{}");
            throw new IllegalStateException("simulated failed commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages", Integer.class)).isZero();
        assertThat(outbox.claim()).isEmpty();
        assertThatThrownBy(() -> store.capture(request, writer -> {
            writer.jsonLines("profile", rows -> rows.add(Map.of("name", "fixture")));
            throw new IOException("private failure detail");
        })).isInstanceOf(ExportSnapshotStore.ExportFailure.class).hasMessage("EXPORT_SOURCE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_entries", Integer.class)).isZero();
    }

    @Test void replayKeepsTheSameSnapshotAndCannotChangeItsOwnerOrDeadline() {
        var request = request();
        var original = store.capture(request, writer -> writer.jsonLines("profile", rows -> rows.add(Map.of("name", "first"))));
        var replay = store.capture(request, writer -> { throw new AssertionError("Projection ran twice"); });
        assertThat(replay).isEqualTo(original);
        assertStatus(404, () -> store.manifest(request.jobId(), UUID.randomUUID()));
        assertStatus(404, () -> store.page(request.jobId(), UUID.randomUUID(), 0, 0));
        assertStatus(409, () -> store.capture(new ExportSnapshotStore.Request(request.jobId(), UUID.randomUUID(), now, request.expiresAt()), writer -> {}));
        assertStatus(409, () -> store.capture(new ExportSnapshotStore.Request(request.jobId(), owner, now, now.plusSeconds(60)), writer -> {}));
        assertStatus(429, () -> store.capture(request(), writer -> {}));
    }

    @Test void expiryDeletesPagesAndAnExpiredRequestCannotRecreateThem() {
        var request = request();
        store.capture(request, writer -> writer.jsonLines("profile", rows -> rows.add(Map.of("name", "fixture"))));
        var later = at(request.expiresAt());
        assertStatus(410, () -> later.page(request.jobId(), owner, 0, 0));
        later.expire();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages", Integer.class)).isZero();
        assertStatus(410, () -> later.capture(request, writer -> {}));
    }

    @Test void accountDeletionCancelsExistingAndLateSnapshotsAndSurvivesRestart() {
        var request = request();
        store.capture(request, writer -> writer.jsonLines("profile", rows -> rows.add(Map.of("name", "fixture"))));
        store.cancelAccount(owner);
        store.cancelAccount(owner);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages", Integer.class)).isZero();
        var restarted = at(now);
        assertStatus(410, () -> restarted.manifest(request.jobId(), owner));
        assertStatus(410, () -> restarted.capture(request, writer -> {}));
        assertStatus(410, () -> restarted.capture(request(), writer -> {}));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_account_tombstones", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentDuplicateDeliveryCapturesOnce() throws Exception {
        var request = request();
        var start = new CountDownLatch(1);
        var captures = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<ExportSnapshotStore.Manifest> call = () -> {
                start.await();
                return at(now).capture(request, writer -> {
                    captures.incrementAndGet();
                    writer.jsonLines("profile", rows -> rows.add(Map.of("name", "fixture")));
                });
            };
            var first = pool.submit(call); var second = pool.submit(call); start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(captures).hasValue(1);
    }

    @Test void cancellingAnUnseenJobBlocksLateCaptureButAllowsANewExportForTheSameAccount() {
        var cancelled = request();
        store.cancelJob(cancelled);
        assertStatus(410, () -> store.capture(cancelled, writer -> {}));
        var fresh = request();
        var manifest = store.capture(fresh, writer -> writer.jsonLines("profile", rows -> rows.add(Map.of("name", "fresh"))));
        assertThat(manifest.jobId()).isEqualTo(fresh.jobId());
        store.cancelJob(fresh);
        assertStatus(410, () -> store.page(fresh.jobId(), owner, 0, 0));
    }

    @Test void receivedManifestCannotChangeArchivePathsOrChunkBounds() {
        var invalid = new ExportSnapshotStore.Manifest(1, "auth", UUID.randomUUID(), owner, now, now.plusSeconds(60),
                java.util.List.of(new ExportSnapshotStore.Entry(0, "../outside", "application/x-ndjson", 2, 1, "a".repeat(64))));
        assertThatThrownBy(invalid::fingerprint).isInstanceOf(IllegalArgumentException.class);
        var badCount = new ExportSnapshotStore.Manifest(1, "auth", UUID.randomUUID(), owner, now, now.plusSeconds(60),
                java.util.List.of(new ExportSnapshotStore.Entry(0, "profile.jsonl", "application/x-ndjson", 2, 100, "a".repeat(64))));
        assertThatThrownBy(badCount::fingerprint).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void retainedPeerContentAndFilesRequireCurrentOwningScopeWhileAuthoredRowsStaySeparate() {
        UUID peer = UUID.randomUUID(); UUID resource = UUID.randomUUID(); var request = request();
        var manifest = store.capture(request, writer -> {
            writer.jsonLines("authored_messages", rows -> rows.add(Map.of("body", "My text")));
            writer.protectedJsonLines("conversations", peer, new ExportSnapshotStore.AccessScope("DM_PEER", peer),
                    rows -> rows.add(Map.of("body", "Received private text")));
            writer.file(resource, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        });
        assertThat(manifest.fingerprint()).hasSize(64);
        store.requireAccess(request.jobId(), owner, 0, ExportSnapshotAccess.denyProtected());
        assertStatus(503, () -> store.requireAccess(request.jobId(), owner, null, ExportSnapshotAccess.denyProtected()));
        var checked = new java.util.ArrayList<String>();
        ExportSnapshotAccess granted = (account, kind, target) -> {
            assertThat(account).isEqualTo(owner); checked.add(kind + ":" + target);
        };
        store.requireAccess(request.jobId(), owner, 1, granted);
        assertThat(checked).containsExactly("DM_PEER:" + peer);
        checked.clear(); store.requireAccess(request.jobId(), owner, 2, granted);
        assertThat(checked).containsExactly("RESOURCE:" + resource);
        ExportSnapshotAccess revoked = (account, kind, target) -> { throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "CURRENT_ACCESS_REVOKED"); };
        assertStatus(403, () -> store.requireAccess(request.jobId(), owner, 1, revoked));
        assertStatus(403, () -> store.requireAccess(request.jobId(), owner, null, revoked));
        assertStatus(404, () -> store.requireAccess(request.jobId(), UUID.randomUUID(), 1, granted));
    }

    @Test void oversizedRecordsAndUnsafeEntryNamesRollbackWithoutAPartialExport() {
        var request = request();
        assertThatThrownBy(() -> store.capture(request, writer -> writer.jsonLines("../../private", rows -> {})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.capture(request, writer -> writer.jsonLines("profile", rows -> rows.add("x".repeat(ExportSnapshotStore.PAGE_BYTES)))))
                .isInstanceOf(ExportSnapshotStore.ExportFailure.class).hasMessage("EXPORT_RECORD_TOO_LARGE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_pages", Integer.class)).isZero();
        assertStatus(400, () -> store.capture(new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86_401)), writer -> {}));
    }

    private ExportSnapshotStore at(Instant instant) { return new ExportSnapshotStore(jdbc, tx, new ObjectMapper(), Clock.fixed(instant, ZoneOffset.UTC), "auth"); }
    private ExportSnapshotStore.Request request() { return new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86_400)); }
    private static void assertStatus(int code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(code));
    }
}
