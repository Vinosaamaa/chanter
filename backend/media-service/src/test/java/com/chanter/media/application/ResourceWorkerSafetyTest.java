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
    @MockitoSpyBean TestCourseResourceAccessClient access;
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

    @Test void extractionOutcomeIsSeparateFromDownloadAvailabilityAndOnlyFailureCanRetry() {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        doReturn(new ResourceIngestionClient.Outcome("OCR_REQUIRED", java.util.Set.of()))
                .when(ingestion).status(eq(resource.id()), any(), eq(resource.sha256()));
        worker.runOnce();
        var current = service.getCourseResource(resource.id(), teacher);
        assertThat(current.publicStatus()).isEqualTo("AVAILABLE");
        assertThat(current.ingestionStatus()).isEqualTo("OCR_REQUIRED");
        assertThat(lifecycle.claim(false)).isEmpty();
        assertThatThrownBy(() -> service.retryIngestion(resource.id(), teacher)).isInstanceOf(ResponseStatusException.class);
        jdbc.sql("UPDATE course_resources SET ingestion_status='FAILED',retry_at=NULL WHERE id=:id").param("id", resource.id()).update();
        assertThatThrownBy(() -> service.retryIngestion(resource.id(), learner)).isInstanceOf(ResponseStatusException.class);
        service.retryIngestion(resource.id(), teacher);
        assertThat(service.getCourseResource(resource.id(), teacher).ingestionStatus()).isEqualTo("PENDING");
        var job = lifecycle.claim(false).orElseThrow();
        assertThat(service.getCourseResource(resource.id(), teacher).ingestionStatus()).isEqualTo("PROCESSING");
        lifecycle.finishIndex(resource.id(), job.leaseId(), new ResourceIngestionClient.Outcome("READY", java.util.Set.of("VISUAL_CONTENT_NOT_EXTRACTED")));
        current = service.getCourseResource(resource.id(), teacher);
        assertThat(current.ingestionStatus()).isEqualTo("READY");
        assertThat(current.ingestionSignals()).containsExactly("VISUAL_CONTENT_NOT_EXTRACTED");
        assertThatThrownBy(() -> service.retryIngestion(resource.id(), teacher)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void ingestionSourceRequiresCurrentApprovalCourseHashAndAvailability() throws Exception {
        var resource = upload(UUID.randomUUID());
        assertThatThrownBy(() -> service.downloadForIngestion(resource.id(), course, resource.sha256()))
                .isInstanceOf(ResponseStatusException.class);
        worker.runOnce();
        assertThatThrownBy(() -> service.downloadForIngestion(resource.id(), UUID.randomUUID(), resource.sha256()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.downloadForIngestion(resource.id(), course, "0".repeat(64)))
                .isInstanceOf(ResponseStatusException.class);
        try (var content = service.downloadForIngestion(resource.id(), course, resource.sha256()).content()) {
            assertThat(new String(content.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("bounded notes");
        }
        doAnswer(call -> {
            var content = call.callRealMethod();
            jdbc.sql("UPDATE course_resources SET ai_approved=FALSE WHERE id=:id").param("id", resource.id()).update();
            return content;
        }).when(storage).open(resource.storageKey());
        assertThatThrownBy(() -> service.downloadForIngestion(resource.id(), course, resource.sha256()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.downloadForIngestion(resource.id(), course, resource.sha256()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void expiredIndexLeaseRecoversProcessingAndDeletionRejectsLateOutcome() {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        var old = lifecycle.claim(false).orElseThrow();
        assertThat(lifecycle.find(resource.id()).orElseThrow().ingestionStatus()).isEqualTo("PROCESSING");
        jdbc.sql("UPDATE course_resources SET lease_until=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        var replacement = lifecycle.claim(false).orElseThrow();
        lifecycle.finishIndex(resource.id(), old.leaseId(), new ResourceIngestionClient.Outcome("READY", java.util.Set.of()));
        assertThat(lifecycle.find(resource.id()).orElseThrow().ingestionStatus()).isEqualTo("PROCESSING");
        service.deleteCourseResource(resource.id(), teacher);
        lifecycle.finishIndex(resource.id(), replacement.leaseId(), new ResourceIngestionClient.Outcome("READY", java.util.Set.of()));
        worker.runOnce();
        assertThat(lifecycle.find(resource.id()).orElseThrow().state()).isEqualTo("DELETED");
        assertThat(service.usage(course, teacher).reservedBytes()).isZero();
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
        var importer = new ResourceWorker(lifecycle, storage, legacy, validator, scanner, ingestion, access, Clock.systemUTC(), false, true);
        importer.runOnce();
        assertThat(lifecycle.find(id).orElseThrow().state()).isEqualTo("QUARANTINED");
        assertThat(ingestion.purgeCalls()).containsExactly(id);
        assertThat(ingestion.deleteCalls()).isEmpty();
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
        var reconciler = new ResourceWorker(lifecycle, storage, legacy, validator, scanner, ingestion, access, Clock.systemUTC(), true, false);
        reconciler.reconcile();
        verify(storage).delete(orphan); verify(storage, never()).delete(resource.storageKey());
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
    }

    @Test void deletionKeepsReservationUntilAgentConfirmsItsTerminalFence() {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        service.deleteCourseResource(resource.id(), teacher);
        doThrow(new IllegalStateException("agent deletion not confirmed")).when(ingestion).deleteResourceChunks(resource.id());
        worker.runOnce();
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
        assertThat(lifecycle.find(resource.id()).orElseThrow().state()).isEqualTo("DELETE_PENDING");
        doAnswer(call -> {
            assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
            return call.callRealMethod();
        }).when(ingestion).deleteResourceChunks(resource.id());
        jdbc.sql("UPDATE course_resources SET retry_at=NULL").update(); worker.runOnce();
        assertThat(service.usage(course, teacher).reservedBytes()).isZero();
        assertThat(lifecycle.find(resource.id()).orElseThrow().state()).isEqualTo("DELETED");
        assertThat(ingestion.purgeCalls()).isEmpty();
    }

    @Test void requestBudgetFailureIsReportedWhileCleanupKeepsItsReservation() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Private storage request budget reached")).when(storage).put(anyString(), any(), anyString());
        assertThatThrownBy(() -> upload(UUID.randomUUID())).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
        assertThat(jdbc.sql("SELECT state FROM course_resources").query(String.class).single()).isEqualTo("DELETE_PENDING");
        assertThat(service.usage(course, teacher).reservedBytes()).isPositive();
        assertThat(service.listCourseResources(course, learner)).isEmpty();
    }

    @Test void failedLegacyMigrationRetriesMigrationAndPreservesOriginalWhenExhausted() throws Exception {
        UUID id = UUID.randomUUID(); byte[] content = "legacy retry notes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path original = Path.of("target/media-test", id.toString()); Files.createDirectories(original.getParent()); Files.write(original, content);
        jdbc.sql("""
                INSERT INTO course_resources (id,course_id,title,file_name,content_type,byte_size,storage_key,ai_approved,uploaded_by_user_id,created_at,updated_at)
                VALUES (:id,:course,'Legacy','legacy.txt','text/plain',:size,:key,TRUE,:user,:now,:now)
                """).param("id", id).param("course", course).param("size", content.length).param("key", id.toString()).param("user", teacher)
                .param("now", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)).update();
        jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=:size").param("size", content.length).update();
        var importer = new ResourceWorker(lifecycle, storage, legacy, validator, scanner, ingestion, access, Clock.systemUTC(), false, true);
        doThrow(new IllegalStateException("index temporarily unavailable")).when(ingestion).purgeResourceChunks(id);
        for (int attempt = 0; attempt < 5; attempt++) {
            jdbc.sql("UPDATE course_resources SET retry_at=NULL").update();
            importer.runOnce();
        }
        verify(ingestion, times(5)).purgeResourceChunks(id);
        jdbc.sql("UPDATE course_resources SET retry_at=NULL,updated_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        assertThat(lifecycle.claim(true)).isEmpty();
        assertThat(Files.readAllBytes(original)).isEqualTo(content);
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(content.length);
        verify(storage, never()).delete(anyString());
        // An explicit operator retry remains migration work and reuses the reserved migration key.
        jdbc.sql("UPDATE course_resources SET attempts=0").update();
        doCallRealMethod().when(ingestion).purgeResourceChunks(id);
        importer.runOnce();
        assertThat(lifecycle.find(id).orElseThrow().state()).isEqualTo("QUARANTINED");
        assertThat(Files.readAllBytes(original)).isEqualTo(content);
    }

    @Test void cleanResourcesStayAvailableAndReservedAcrossIndexFailuresUntilIndexRetrySucceeds() throws Exception {
        var resource = upload(UUID.randomUUID());
        doThrow(new IllegalStateException("index unavailable")).when(ingestion).status(any(), any(), anyString());
        worker.runOnce();
        assertThat(service.getCourseResource(resource.id(), learner).publicStatus()).isEqualTo("AVAILABLE");
        worker.runOnce();
        assertThat(jdbc.sql("SELECT ingestion_status FROM course_resources WHERE id=:id").param("id", resource.id()).query(String.class).single()).isEqualTo("PROCESSING");
        jdbc.sql("UPDATE course_resources SET attempts=20,retry_at=NULL,updated_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z'").update();
        worker.runOnce();
        assertThat(service.getCourseResource(resource.id(), learner).publicStatus()).isEqualTo("AVAILABLE");
        assertThat(service.usage(course, teacher).reservedBytes()).isEqualTo(resource.byteSize());
        verify(storage, never()).delete(resource.storageKey());
        doCallRealMethod().when(ingestion).status(any(), any(), anyString());
        jdbc.sql("UPDATE course_resources SET retry_at=NULL").update(); worker.runOnce();
        assertThat(jdbc.sql("SELECT ingestion_status FROM course_resources WHERE id=:id").param("id", resource.id()).query(String.class).single()).isEqualTo("READY");
    }

    @Test void durableEventCarriesAuthoritativeScopeAndRevocationFencesOldStatus() {
        var resource = upload(UUID.randomUUID());
        assertThat(agentPayloads(resource.id())).isEmpty();
        worker.runOnce();
        var published = lifecycle.find(resource.id()).orElseThrow();
        assertThat(published.studyServerId()).isEqualTo(access.requireStudyServerId(course));
        assertThat(published.ingestionEventId()).isNotNull();
        assertThat(agentPayloads(resource.id())).singleElement().asString()
                .contains(course.toString(), published.studyServerId().toString(), resource.sha256(), "\"aiApproved\":true")
                .doesNotContain("storageKey", "contentBase64");
        var stale = lifecycle.claim(false).orElseThrow();
        assertThatThrownBy(() -> service.setAiApproved(resource.id(), learner, false)).isInstanceOf(ResponseStatusException.class);
        service.setAiApproved(resource.id(), teacher, false);
        lifecycle.synchronizeIndex(resource.id(), stale.leaseId(), published.ingestionEventId(), new ResourceIngestionClient.Outcome("READY", java.util.Set.of()));
        assertThat(service.getCourseResource(resource.id(), learner).aiApproved()).isFalse();
        assertThat(service.getCourseResource(resource.id(), learner).ingestionStatus()).isEqualTo("NONE");
        assertThat(agentPayloads(resource.id())).hasSize(2);
        service.setAiApproved(resource.id(), teacher, false);
        assertThat(agentPayloads(resource.id())).hasSize(2);
        verify(ingestion, never()).ingestAiApprovedResource(any(), any(), anyString(), any());
    }

    @Test void exhaustedEventDeliveryIsVisibleAndManualRetryPublishesNewIdentity() {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        var previous = lifecycle.find(resource.id()).orElseThrow().ingestionEventId();
        jdbc.sql("UPDATE durable_outbox SET status='FAILED' WHERE id=:id").param("id", previous).update();
        worker.runOnce();
        assertThat(lifecycle.find(resource.id()).orElseThrow().ingestionStatus()).isEqualTo("FAILED");
        assertThat(lifecycle.claim(false)).isEmpty();
        service.retryIngestion(resource.id(), teacher);
        assertThat(lifecycle.find(resource.id()).orElseThrow().ingestionEventId()).isNotEqualTo(previous);
        assertThat(agentPayloads(resource.id())).hasSize(2);
    }

    private java.util.List<String> agentPayloads(UUID resource) {
        return jdbc.sql("SELECT payload FROM durable_outbox WHERE destination='agent' AND aggregate_key=:key ORDER BY revision")
                .param("key", "RESOURCE:" + resource).query(String.class).list();
    }

    @Test void missingLegacyCourseStopsScopeRetryWithVisibleFailure() {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        jdbc.sql("UPDATE course_resources SET study_server_id=NULL,ingestion_event_id=NULL,ingestion_status='PENDING' WHERE id=:id")
                .param("id", resource.id()).update();
        access.clear();
        worker.runOnce();
        assertThat(lifecycle.find(resource.id()).orElseThrow().ingestionStatus()).isEqualTo("FAILED");
        assertThat(lifecycle.claim(false)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 404, 408, 410, 422, 429, 503})
    void legacyScopeSeparatesPermanentFailuresFromRetryableResponses(int status) {
        var resource = upload(UUID.randomUUID()); worker.runOnce();
        jdbc.sql("UPDATE course_resources SET study_server_id=NULL,ingestion_event_id=NULL,ingestion_status='PENDING' WHERE id=:id")
                .param("id", resource.id()).update();
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(status)))
                .when(access).requireStudyServerId(course);
        worker.runOnce();
        boolean retryable = status == 408 || status == 429 || status >= 500;
        assertThat(lifecycle.find(resource.id()).orElseThrow().ingestionStatus())
                .isEqualTo(retryable ? "PROCESSING" : "FAILED");
        if (retryable) {
            jdbc.sql("UPDATE course_resources SET retry_at=NULL WHERE id=:id").param("id", resource.id()).update();
            assertThat(lifecycle.claim(false)).isPresent();
        } else assertThat(lifecycle.claim(false)).isEmpty();
    }
}
