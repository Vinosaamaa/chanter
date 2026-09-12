package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chanter.media.domain.CourseResource;
import com.chanter.media.infra.TestCourseResourceAccessClient;
import com.chanter.media.infra.TestResourceIngestionClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class ResourceWorkerSafetyTest {
    @Autowired CourseResourceService service;
    @Autowired ResourceWorker worker;
    @Autowired ResourceLifecycle lifecycle;
    @Autowired JdbcClient jdbc;
    @Autowired TestCourseResourceAccessClient access;
    @MockitoSpyBean TestResourceIngestionClient ingestion;
    @Autowired LocalCourseResourceStorage legacy;
    @Autowired UploadValidator validator;
    @MockitoBean MalwareScanner scanner;
    @MockitoSpyBean PrivateResourceStorage storage;
    UUID course, teacher, learner;

    @BeforeEach void resetData() throws Exception {
        jdbc.sql("DELETE FROM course_resources").update();
        jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=0,foreground_requests=0,maintenance_requests=0").update();
        access.clear(); ingestion.clear();
        course = UUID.randomUUID(); teacher = UUID.randomUUID(); learner = UUID.randomUUID();
        access.grantInstructorUpload(course, teacher); access.grantLearnerView(course, learner);
        doReturn(MalwareScanner.Verdict.CLEAN).when(scanner).scan(any());
    }
    private CourseResource upload(UUID key) {
        return service.uploadCourseResource(course, teacher, "Notes", true,
                new MockMultipartFile("file", "notes.txt", "text/plain", "bounded notes".getBytes(java.nio.charset.StandardCharsets.UTF_8)), key, null);
    }
    private void notAvailable(UUID id, int status) {
        assertThatThrownBy(() -> service.downloadCourseResource(id, learner)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(status));
    }

    @Test void infectedFileNeverReachesLearnersOrAiAndRetryKeepsItsIdentityAfterCleanup() throws Exception {
        when(scanner.scan(any())).thenReturn(MalwareScanner.Verdict.INFECTED);
        UUID key = UUID.randomUUID(); var resource = upload(key); worker.runOnce();
        assertThat(service.getCourseResource(resource.id(), teacher).publicStatus()).isEqualTo("REJECTED");
        assertThat(service.listCourseResources(course, learner)).isEmpty(); notAvailable(resource.id(), 409);
        assertThat(ingestion.ingestCalls()).isEmpty();
        assertThat(upload(key).id()).isEqualTo(resource.id());
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
        worker.runOnce();
        assertThat(service.usage(course, teacher).reservedBytes()).isZero();
        assertThat(upload(key).publicStatus()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> storage.open(resource.storageKey())).isInstanceOf(IOException.class);
    }

    @Test void unavailableScannerFailsClosedThenRetriesWithoutReupload() throws Exception {
        when(scanner.scan(any())).thenThrow(new IOException("unavailable"));
        UUID key = UUID.randomUUID(); var resource = upload(key); worker.runOnce();
        assertThat(upload(key).publicStatus()).isEqualTo("FAILED"); notAvailable(resource.id(), 409);
        assertThat(ingestion.ingestCalls()).isEmpty(); assertThat(service.listCourseResources(course, learner)).isEmpty();
        doReturn(MalwareScanner.Verdict.CLEAN).when(scanner).scan(any());
        jdbc.sql("UPDATE course_resources SET retry_at=NULL").update(); worker.runOnce();
        assertThat(service.getCourseResource(resource.id(), learner).publicStatus()).isEqualTo("AVAILABLE");
        verify(storage, times(1)).put(eq(resource.storageKey()), any(), anyString());
    }

    @Test void uncertainPutAndFailedDeleteKeepQuotaAndNeverExposeBytes() throws Exception {
        doAnswer(call -> { call.callRealMethod(); throw new IOException("response interrupted after write"); }).when(storage).put(anyString(), any(), anyString());
        UUID key = UUID.randomUUID(); var resource = upload(key);
        assertThat(resource.publicStatus()).isEqualTo("FAILED"); notAvailable(resource.id(), 404);
        assertThat(upload(key).id()).isEqualTo(resource.id());
        doThrow(new IOException("unavailable")).when(storage).delete(resource.storageKey()); worker.runOnce();
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
        doCallRealMethod().when(storage).delete(resource.storageKey());
        jdbc.sql("UPDATE course_resources SET retry_at=NULL").update(); worker.runOnce();
        assertThat(service.usage(course, teacher).reservedBytes()).isZero();
        assertThat(upload(key).id()).isEqualTo(resource.id()); verify(storage, times(1)).put(anyString(), any(), anyString());
    }

    @Test void corruptDownloadFailsBeforeReturningContentAndAuthorizationDoesNotReadStorage() throws Exception {
        var resource = upload(UUID.randomUUID()); worker.runOnce(); clearInvocations(storage);
        assertThatThrownBy(() -> service.downloadCourseResource(resource.id(), UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verify(storage, never()).open(anyString());
        doReturn(new java.io.ByteArrayInputStream("changed notes".getBytes())).when(storage).open(resource.storageKey());
        notAvailable(resource.id(), 503);
        try (var spools = Files.list(Path.of("target/media-spool-test"))) {
            assertThat(spools.filter(p -> p.getFileName().toString().startsWith("download-")).count()).isZero();
        }
    }

    @Test void deletionDuringObjectReadWinsAndRepeatedDeleteIsSafe() throws Exception {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        doAnswer(call -> {
            var input = (java.io.InputStream) call.callRealMethod();
            service.deleteCourseResource(resource.id(), teacher);
            return input;
        }).when(storage).open(resource.storageKey());
        notAvailable(resource.id(), 404);
        service.deleteCourseResource(resource.id(), teacher); worker.runOnce(); service.deleteCourseResource(resource.id(), teacher);
        assertThat(service.usage(course, teacher).reservedBytes()).isZero();
        assertThat(ingestion.deleteCalls()).containsExactly(resource.id());
    }

    @Test void legacyImportPreservesOriginalAndResumesAnUncertainImmutablePutBeforeScanning() throws Exception {
        UUID id = UUID.randomUUID(); byte[] content = "legacy notes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path original = Path.of("target/media-test", id.toString()); Files.createDirectories(original.getParent()); Files.write(original, content);
        jdbc.sql("""
                INSERT INTO course_resources (id,course_id,title,file_name,content_type,byte_size,storage_key,ai_approved,uploaded_by_user_id,created_at,updated_at)
                VALUES (:id,:course,'Legacy','legacy.txt','text/plain',:size,:key,TRUE,:user,:now,:now)
                """).param("id", id).param("course", course).param("size", content.length).param("key", id.toString()).param("user", teacher)
                .param("now", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)).update();
        jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=:size").param("size", content.length).update();
        notAvailable(id, 409);
        doAnswer(call -> { call.callRealMethod(); throw new IOException("lost response"); }).when(storage).put(anyString(), any(), anyString());
        var importer = new ResourceWorker(lifecycle, storage, legacy, validator, scanner, ingestion, Clock.systemUTC(), false, true);
        importer.runOnce();
        assertThat(lifecycle.find(id).orElseThrow().state()).isEqualTo("QUARANTINED");
        assertThat(ingestion.deleteCalls()).containsExactly(id);
        assertThat(ingestion.ingestCalls()).isEmpty();
        assertThat(Files.readAllBytes(original)).isEqualTo(content); notAvailable(id, 409);
        importer.runOnce();
        try (var download = service.downloadCourseResource(id, learner).content()) { assertThat(download.readAllBytes()).isEqualTo(content); }
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(content.length);
    }

    @Test void reconcilerDeletesOnlyOldUnreferencedKeysAndKeepsReservedQuarantine() throws Exception {
        var resource = upload(UUID.randomUUID());
        String orphan = PrivateResourceStorage.PREFIX + course + "/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        doReturn(new PrivateResourceStorage.Page(java.util.List.of(
                new PrivateResourceStorage.ObjectInfo(resource.storageKey(), Instant.now().minusSeconds(90000)),
                new PrivateResourceStorage.ObjectInfo(orphan, Instant.now().minusSeconds(90000))), null)).when(storage).list(null);
        var reconciler = new ResourceWorker(lifecycle, storage, legacy, validator, scanner, ingestion, Clock.systemUTC(), true, false);
        reconciler.reconcile();
        verify(storage).delete(orphan); verify(storage, never()).delete(resource.storageKey());
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
    }

    @Test void cleanResourcesStayAvailableAndReservedAcrossIndexFailuresUntilIndexRetrySucceeds() throws Exception {
        var resource = upload(UUID.randomUUID());
        doThrow(new IllegalStateException("index unavailable")).when(ingestion).ingestAiApprovedResource(any(), any(), anyString(), any());
        worker.runOnce();
        assertThat(service.getCourseResource(resource.id(), learner).publicStatus()).isEqualTo("AVAILABLE");
        worker.runOnce();
        assertThat(jdbc.sql("SELECT ingestion_status FROM course_resources WHERE id=:id").param("id", resource.id()).query(String.class).single()).isEqualTo("FAILED");
        jdbc.sql("UPDATE course_resources SET attempts=20,retry_at=NULL,updated_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        worker.runOnce();
        assertThat(service.getCourseResource(resource.id(), learner).publicStatus()).isEqualTo("AVAILABLE");
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
        verify(storage, never()).delete(resource.storageKey());
        doCallRealMethod().when(ingestion).ingestAiApprovedResource(any(), any(), anyString(), any());
        jdbc.sql("UPDATE course_resources SET retry_at=NULL").update(); worker.runOnce();
        assertThat(jdbc.sql("SELECT ingestion_status FROM course_resources WHERE id=:id").param("id", resource.id()).query(String.class).single()).isEqualTo("COMPLETE");
    }
}
