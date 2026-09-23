package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ExportArchiveTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final List<ExportSnapshotStore.Manifest> manifests = new ArrayList<>();
    final Map<String, ExportSnapshotStore> stores = new HashMap<>();
    final byte[] binary = new byte[ExportSnapshotStore.PAGE_BYTES + 13];
    @BeforeEach void setup() {
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) (i % 251);
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(86400));
        for (String source : AccountExportProtocol.SOURCES.stream().sorted().toList()) {
            var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(data); jdbc.execute(ExportSnapshotStore.SCHEMA);
            var store = new ExportSnapshotStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(data)), mapper, Clock.fixed(now, ZoneOffset.UTC), source);
            stores.put(source, store);
            manifests.add(store.capture(request, output -> {
                output.jsonLines("own_data", rows -> rows.add(Map.of("text", "My résumé 学習", "source", source)));
                if (source.equals("media")) output.file(new UUID(1, 1), new ByteArrayInputStream(binary));
            }));
        }
    }
    @Test void archiveContainsEveryBoundedSourceAndExactBinaryBytesWithACompletedDirectory() throws Exception {
        var output = new ByteArrayOutputStream();
        ExportArchive.write(output, manifests, mapper, this::page, () -> { });
        byte[] zip = output.toByteArray(); assertThat(completed(zip)).isTrue();
        var entries = new HashMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) entries.put(entry.getName(), input.readAllBytes());
        }
        assertThat(entries).hasSize(9);
        assertThat(entries.get("media/files/00000000-0000-0001-0000-000000000001/content")).isEqualTo(binary);
        assertThat(new String(entries.get("auth/own_data.jsonl"), StandardCharsets.UTF_8)).contains("My résumé 学習");
        assertThat(new String(entries.get("manifest.json"), StandardCharsets.UTF_8)).contains("Separate source snapshots", "schemaVersion");
    }
    @Test void modifiedOrTruncatedPageCannotBecomeAValidPartialArchive() {
        for (boolean truncate : List.of(false, true)) {
            var output = new ByteArrayOutputStream();
            assertThatThrownBy(() -> ExportArchive.write(output, manifests, mapper, (manifest, entry, ordinal) -> {
                byte[] bytes = page(manifest, entry, ordinal);
                if (truncate) return java.util.Arrays.copyOf(bytes, bytes.length - 1);
                bytes[0] ^= 1; return bytes;
            }, () -> { })).isInstanceOf(IOException.class);
            assertThat(completed(output.toByteArray())).isFalse();
        }
    }
    @Test void revocationDuringRemotePageFetchAbortsBeforeThoseBytesAreWrittenAndNeverFinalizes() {
        var output = new ByteArrayOutputStream(); var fetched = new AtomicInteger();
        assertThatThrownBy(() -> ExportArchive.write(output, manifests, mapper,
                (manifest, entry, ordinal) -> { fetched.incrementAndGet(); return page(manifest, entry, ordinal); },
                () -> { if (fetched.get() > 0) throw new IllegalStateException("ACCESS_REVOKED"); }))
                .hasMessage("ACCESS_REVOKED");
        assertThat(fetched).hasValue(1);
        assertThat(completed(output.toByteArray())).isFalse();
    }
    @Test void missingOrMixedAccountManifestIsRejectedBeforeAnyOutput() {
        var output = new ByteArrayOutputStream();
        assertThatThrownBy(() -> ExportArchive.write(output, manifests.subList(0, 6), mapper, this::page, () -> { })).isInstanceOf(IllegalArgumentException.class);
        var mixed = new ArrayList<>(manifests); var first = mixed.getFirst();
        mixed.set(0, new ExportSnapshotStore.Manifest(1, first.source(), first.jobId(), UUID.randomUUID(), first.capturedAt(), first.expiresAt(), first.entries()));
        assertThatThrownBy(() -> ExportArchive.write(output, mixed, mapper, this::page, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThat(output.size()).isZero();
    }
    private byte[] page(ExportSnapshotStore.Manifest manifest, int entry, int ordinal) {
        return stores.get(manifest.source()).page(manifest.jobId(), manifest.accountId(), entry, ordinal);
    }
    private static boolean completed(byte[] bytes) {
        int offset = bytes.length - 22;
        return offset >= 0 && bytes[offset] == 'P' && bytes[offset + 1] == 'K' && bytes[offset + 2] == 5 && bytes[offset + 3] == 6;
    }
}
