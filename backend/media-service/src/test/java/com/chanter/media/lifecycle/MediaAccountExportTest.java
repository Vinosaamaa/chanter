package com.chanter.media.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.chanter.media.application.CourseResourceService;
import com.chanter.media.domain.CourseResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class MediaAccountExportTest {
    @Test void onlyOwnedAvailableVerifiedBytesAreRetainedAndLateRevocationRollsBackRatherThanClaimingOmission() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(data).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(data); var tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        Instant now = Instant.parse("2026-09-19T00:00:00Z"); UUID owner = UUID.randomUUID(); UUID other = UUID.randomUUID(); UUID course = UUID.randomUUID();
        byte[] bytes = "verified file content 学習".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var available = resource(UUID.randomUUID(), course, owner, "AVAILABLE", hash, now, bytes.length);
        var quarantine = resource(UUID.randomUUID(), course, owner, "QUARANTINED", hash, now, bytes.length);
        var peer = resource(UUID.randomUUID(), course, other, "AVAILABLE", hash, now, bytes.length);
        for (var resource : java.util.List.of(available, quarantine, peer)) jdbc.update("""
            INSERT INTO course_resources(id,course_id,title,file_name,content_type,byte_size,storage_key,ai_approved,uploaded_by_user_id,created_at,state,sha256,storage_backend)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, resource.id(), course, "Own resource", "../original-filename.txt", "text/plain", bytes.length, "PRIVATE_STORAGE_KEY_CANARY", true,
                resource.uploadedByUserId(), Timestamp.from(now), resource.state(), hash, "local");
        var service = mock(CourseResourceService.class); var closed = new AtomicBoolean();
        when(service.getCourseResource(available.id(), owner)).thenReturn(available);
        when(service.getCourseResource(quarantine.id(), owner)).thenReturn(quarantine);
        when(service.downloadCourseResource(available.id(), owner)).thenAnswer(call -> new CourseResourceService.StoredCourseResourceContent(available,
                new ByteArrayInputStream(bytes) { @Override public void close() throws java.io.IOException { closed.set(true); super.close(); } }));
        var exporter = new MediaAccountExport(jdbc, service);
        var store = new ExportSnapshotStore(jdbc, tx, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), "media");
        var request = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86400));
        var manifest = store.capture(request, output -> exporter.capture(owner, output));
        assertThat(closed).isTrue();
        verify(service, never()).downloadCourseResource(quarantine.id(), owner);
        verify(service, never()).getCourseResource(peer.id(), owner);
        var file = manifest.entries().stream().filter(entry -> entry.path().startsWith("files/")).findFirst().orElseThrow();
        assertThat(file.path()).isEqualTo("files/" + available.id() + "/content");
        assertThat(file.sha256()).isEqualTo(hash);
        assertThat(store.page(request.jobId(), owner, file.ordinal(), 0)).isEqualTo(bytes);
        assertThat(new String(store.page(request.jobId(), owner, 0, 0), StandardCharsets.UTF_8)).doesNotContain("PRIVATE_STORAGE_KEY_CANARY", other.toString(), peer.id().toString());
        store.requireAccess(request.jobId(), owner, file.ordinal(), exporter);
        when(service.getCourseResource(available.id(), owner)).thenReturn(resource(available.id(), course, owner, "AVAILABLE", "a".repeat(64), now, bytes.length));
        assertThatThrownBy(() -> store.requireAccess(request.jobId(), owner, file.ordinal(), exporter)).hasMessageContaining("EXPORT_FILE_ACCESS_REVOKED");
        store.cancelJob(request); closed.set(false);
        when(service.getCourseResource(available.id(), owner)).thenReturn(available).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Late access revocation"));
        var fresh = new ExportSnapshotStore.Request(UUID.randomUUID(), owner, now, now.plusSeconds(86400));
        assertThatThrownBy(() -> store.capture(fresh, output -> exporter.capture(owner, output))).hasMessageContaining("Late access revocation");
        assertThat(closed).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_snapshots WHERE id=?", Integer.class, fresh.jobId())).isZero();
    }
    private static CourseResource resource(UUID id, UUID course, UUID owner, String state, String hash, Instant now, long bytes) {
        return new CourseResource(id, course, "Own resource", "original.txt", "text/plain", bytes, "PRIVATE_STORAGE_KEY_CANARY", true, owner, now,
                state, hash, UUID.randomUUID(), "local");
    }
}
