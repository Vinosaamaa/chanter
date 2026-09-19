package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;

import com.chanter.media.infra.TestCourseResourceAccessClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs against real PostgreSQL, S3Mock and ClamAV processes; never substitutes a scan verdict. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MEDIA_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrivateStorageIntegrationTest {
    static final UUID COURSE = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    static final UUID TEACHER = UUID.fromString("a0000000-0000-4000-8000-000000000002");
    static final UUID LEARNER = UUID.fromString("a0000000-0000-4000-8000-000000000003");
    static final UUID RETRY = UUID.fromString("a0000000-0000-4000-8000-000000000004");
    static final byte[] CLEAN = "Durable private Course Resource".getBytes(StandardCharsets.UTF_8);
    @Autowired CourseResourceService service;
    @Autowired ResourceWorker worker;
    @Autowired ResourceLifecycle lifecycle;
    @Autowired PrivateResourceStorage storage;
    @Autowired MalwareScanner scanner;
    @Autowired TestCourseResourceAccessClient access;
    @Autowired JdbcClient jdbc;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5544/chanter_media");
        registry.add("spring.datasource.username", () -> "media_test");
        registry.add("spring.datasource.password", () -> "media-test-only-password");
        registry.add("chanter.media.storage-backend", () -> "s3");
        registry.add("chanter.media.s3.endpoint", () -> "http://127.0.0.1:9090");
        registry.add("chanter.media.s3.region", () -> "us-east-1");
        registry.add("chanter.media.s3.bucket", () -> "private-media-test");
        registry.add("chanter.media.s3.access-key", () -> "emulator-only");
        registry.add("chanter.media.s3.secret-key", () -> "emulator-only");
        registry.add("chanter.media.s3.allow-local-http", () -> true);
        registry.add("chanter.media.scanner.socket-path", () -> System.getenv("CHANTER_CLAMAV_SOCKET_PATH"));
    }
    @BeforeAll void setup() {
        access.grantInstructorUpload(COURSE, TEACHER); access.grantLearnerView(COURSE, LEARNER);
        if (!"true".equals(System.getenv("MEDIA_RESTART_PHASE"))) {
            jdbc.sql("DELETE FROM course_resources").update();
            jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=0,foreground_requests=0,maintenance_requests=0").update();
        }
    }
    private com.chanter.media.domain.CourseResource uploadClean() {
        return service.uploadCourseResource(COURSE, TEACHER, "Durable notes", false,
                new MockMultipartFile("file", "notes.txt", "text/plain", CLEAN), RETRY, null);
    }
    @Test @Order(1) @EnabledIfEnvironmentVariable(named = "MEDIA_RESTART_PHASE", matches = "false")
    void actualStorageAndCleanScanGateDownload() throws Exception {
        var resource = uploadClean(); assertThat(resource.publicStatus()).isEqualTo("PROCESSING");
        assertThat(service.listCourseResources(COURSE, LEARNER)).isEmpty();
        worker.runOnce();
        assertThat(lifecycle.find(resource.id()).orElseThrow().publicStatus()).isEqualTo("AVAILABLE");
        try (var content = service.downloadCourseResource(resource.id(), LEARNER).content()) { assertThat(content.readAllBytes()).isEqualTo(CLEAN); }
    }
    @Test @Order(2) @EnabledIfEnvironmentVariable(named = "MEDIA_RESTART_PHASE", matches = "false")
    void realClamAvRejectsEicarInsidePresentationAndDeleteReleasesReservation() throws Exception {
        // EICAR is the standard harmless antivirus test string. Splitting prevents source scanners mistaking this fixture for an uploaded file.
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : java.util.List.of("[Content_Types].xml", "ppt/presentation.xml", "fixture.txt")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write((name.endsWith(".txt") ? eicar : "<?xml version=\"1.0\"?><fixture/>").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        long before = service.usage(COURSE, TEACHER).reservedBytes();
        var resource = service.uploadCourseResource(COURSE, TEACHER, "Scanner fixture", true,
                new MockMultipartFile("file", "fixture.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", bytes.toByteArray()), UUID.randomUUID(), null);
        worker.runOnce(); assertThat(lifecycle.find(resource.id()).orElseThrow().publicStatus()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> service.downloadCourseResource(resource.id(), LEARNER)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        worker.runOnce(); assertThat(service.usage(COURSE, TEACHER).reservedBytes()).isEqualTo(before);
        assertThatThrownBy(() -> storage.open(resource.storageKey())).isInstanceOf(java.io.IOException.class);
    }
    @Test @Order(3) @EnabledIfEnvironmentVariable(named = "MEDIA_RESTART_PHASE", matches = "false")
    void realScannerRejectsNestedContentBeyondItsInspectionLimit() throws Exception {
        Path compressed = Path.of("target/clamav-limit-fixture.gz");
        try (var gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(compressed))) {
            byte[] block = new byte[1024 * 1024];
            for (int index = 0; index < 60; index++) gzip.write(block);
        }
        assertThat(scanner.scan(compressed)).isEqualTo(MalwareScanner.Verdict.INFECTED);
    }
    @Test @Order(4) @EnabledIfEnvironmentVariable(named = "MEDIA_RESTART_PHASE", matches = "false")
    void conditionalWritesCannotReplaceObjectsAndAllAttemptsAreMetered() throws Exception {
        String key = PrivateResourceStorage.PREFIX + UUID.randomUUID() + "/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        Path file = Path.of("target/s3-immutable.txt"); Files.write(file, CLEAN);
        int normalBefore = jdbc.sql("SELECT foreground_requests FROM media_storage_budget WHERE id=1").query(Integer.class).single();
        int deletesBefore = jdbc.sql("SELECT maintenance_requests FROM media_storage_budget WHERE id=1").query(Integer.class).single();
        storage.put(key, file, UploadValidator.checksum(file));
        Files.writeString(file, "replacement");
        assertThatThrownBy(() -> storage.put(key, file, UploadValidator.checksum(file))).isInstanceOf(java.io.IOException.class);
        try (var object = storage.open(key)) { assertThat(object.readAllBytes()).isEqualTo(CLEAN); }
        assertThat(storage.list(null).objects()).anyMatch(object -> object.key().equals(key));
        storage.delete(key);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(java.io.IOException.class);
        assertThat(jdbc.sql("SELECT foreground_requests FROM media_storage_budget WHERE id=1").query(Integer.class).single() - normalBefore).isEqualTo(5);
        assertThat(jdbc.sql("SELECT maintenance_requests FROM media_storage_budget WHERE id=1").query(Integer.class).single() - deletesBefore).isEqualTo(1);
    }
    @Test @Order(5) @EnabledIfEnvironmentVariable(named = "MEDIA_RESTART_PHASE", matches = "true")
    void databaseObjectStoreAndApplicationRestartPreserveIdentityAndContent() throws Exception {
        var resource = uploadClean();
        assertThat(resource.publicStatus()).isEqualTo("AVAILABLE");
        try (var content = service.downloadCourseResource(resource.id(), LEARNER).content()) { assertThat(content.readAllBytes()).isEqualTo(CLEAN); }
        service.deleteCourseResource(resource.id(), TEACHER); worker.runOnce();
        assertThat(service.usage(COURSE, TEACHER).reservedBytes()).isZero();
        assertThat(lifecycle.find(resource.id()).orElseThrow().state()).isEqualTo("DELETED");
    }
}
