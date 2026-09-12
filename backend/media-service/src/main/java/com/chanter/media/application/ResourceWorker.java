package com.chanter.media.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Database leases fence completion; provider calls never hold a database transaction. */
@Component
public class ResourceWorker {
    private static final Logger log = LoggerFactory.getLogger(ResourceWorker.class);
    private final ResourceLifecycle lifecycle;
    private final PrivateResourceStorage storage;
    private final LocalCourseResourceStorage legacy;
    private final UploadValidator validator;
    private final MalwareScanner scanner;
    private final ResourceIngestionClient ingestion;
    private final Clock clock;
    private final boolean enabled;
    private final boolean migrateLegacy;
    private String cursor;

    public ResourceWorker(ResourceLifecycle lifecycle, PrivateResourceStorage storage,
            LocalCourseResourceStorage legacy, UploadValidator validator, MalwareScanner scanner,
            ResourceIngestionClient ingestion, Clock clock,
            @Value("${chanter.media.worker-enabled:true}") boolean enabled,
            @Value("${chanter.media.migrate-legacy:false}") boolean migrateLegacy) {
        this.lifecycle = lifecycle; this.storage = storage; this.legacy = legacy;
        this.validator = validator; this.scanner = scanner; this.ingestion = ingestion;
        this.clock = clock; this.enabled = enabled; this.migrateLegacy = migrateLegacy;
    }

    @Scheduled(fixedDelayString = "${chanter.media.worker-delay-ms:5000}")
    public void poll() { if (enabled) runOnce(); }

    public void runOnce() {
        lifecycle.claim(migrateLegacy).ifPresent(job -> {
            try {
                switch (job.operation()) {
                    case "DELETE" -> delete(job);
                    case "MIGRATE" -> migrate(job);
                    case "SCAN" -> scan(job);
                    case "INDEX" -> index(job);
                    default -> throw new IllegalStateException("Unsupported resource operation");
                }
            } catch (Exception failure) {
                // Provider errors and scanner signatures can contain private information.
                log.warn("Course Resource work deferred resourceId={} operation={}", job.resource().id(), job.operation());
                if (job.operation().equals("DELETE")) lifecycle.retryJob(job.resource().id(), job.leaseId());
                else if (job.operation().equals("INDEX")) lifecycle.finishIndex(job.resource().id(), job.leaseId(), false);
                else lifecycle.finishScan(job.resource().id(), job.leaseId(), "SCAN_FAILED");
            }
        });
    }

    private void scan(ResourceLifecycle.Job job) throws IOException {
        var resource = job.resource();
        requireBackend(resource.storageBackend());
        Path verified = validator.verifiedDownload(storage.open(resource.storageKey()), resource.byteSize(), resource.sha256());
        try {
            var verdict = scanner.scan(verified);
            if (verdict == MalwareScanner.Verdict.INFECTED) {
                lifecycle.finishScan(resource.id(), job.leaseId(), "REJECTED");
            } else if (verdict == MalwareScanner.Verdict.CLEAN) {
                lifecycle.finishScan(resource.id(), job.leaseId(), "AVAILABLE");
            } else throw new IOException("Scanner did not return a verdict");
        } finally { Files.deleteIfExists(verified); }
    }

    private void index(ResourceLifecycle.Job job) throws IOException {
        var resource = job.resource();
        requireBackend(resource.storageBackend());
        Path verified = validator.verifiedDownload(storage.open(resource.storageKey()), resource.byteSize(), resource.sha256());
        try {
            if (lifecycle.find(resource.id()).filter(current -> current.state().equals("AVAILABLE")).isPresent()) {
                ingestion.ingestAiApprovedResource(resource.courseId(), resource.id(), resource.fileName(), Files.readAllBytes(verified));
            }
            lifecycle.finishIndex(resource.id(), job.leaseId(), true);
        } finally { Files.deleteIfExists(verified); }
    }

    private void delete(ResourceLifecycle.Job job) throws IOException {
        var resource = job.resource();
        if (resource.storageBackend().equals("legacy")) legacy.deleteLegacy(resource.id());
        else {
            requireBackend(resource.storageBackend());
            storage.delete(resource.storageKey());
        }
        if (job.migrationKey() != null && !job.migrationKey().equals(resource.storageKey())) storage.delete(job.migrationKey());
        ingestion.deleteResourceChunks(resource.id());
        lifecycle.finishDelete(resource.id(), job.leaseId());
    }

    private void migrate(ResourceLifecycle.Job job) throws IOException {
        var resource = job.resource();
        // Existing vectors predate the quarantine guarantee. Purge them before rebuilding from a clean scan.
        ingestion.deleteResourceChunks(resource.id());
        try (var upload = validator.validateExisting(legacy.legacyPath(resource.id()), resource.fileName(), resource.contentType())) {
            if (upload.byteSize() != resource.byteSize()) throw new IOException("Legacy metadata does not match stored bytes");
            try { storage.put(job.migrationKey(), upload.path(), upload.sha256()); }
            catch (IOException uncertainWrite) {
                // An immutable PUT may have succeeded before its response was interrupted.
                Path confirmed = validator.verifiedDownload(storage.open(job.migrationKey()), upload.byteSize(), upload.sha256());
                Files.deleteIfExists(confirmed);
            }
            lifecycle.finishMigration(job, upload, storage.backend());
        }
    }

    @Scheduled(initialDelayString = "${chanter.media.reconcile-delay-ms:3600000}", fixedDelayString = "${chanter.media.reconcile-delay-ms:3600000}")
    public void reconcile() {
        if (!enabled) return;
        try {
            validator.cleanup(clock.instant().minusSeconds(3600));
            // Prefix and age restrict cleanup to abandoned objects owned by this module.
            for (int pageNumber = 0; pageNumber < 10; pageNumber++) {
                var page = storage.list(cursor);
                for (var object : page.objects()) {
                    PrivateResourceStorage.requireKey(object.key());
                    if (object.modifiedAt().isBefore(clock.instant().minusSeconds(86400)) && lifecycle.orphaned(object.key())) storage.delete(object.key());
                }
                cursor = page.nextCursor();
                if (cursor == null) break;
            }
        } catch (Exception unavailable) { log.warn("Course Resource reconciliation deferred"); }
    }

    private void requireBackend(String backend) throws IOException {
        if (!storage.backend().equals(backend)) throw new IOException("Resource storage migration is required");
    }
}
