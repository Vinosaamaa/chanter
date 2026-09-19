package com.chanter.auth.lifecycle;

import com.chanter.common.lifecycle.ExportArchive;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AccountExportDownloadController {
    private final AccountExportJobs jobs;
    private final ExportSourceClient sources;
    private final ObjectMapper mapper;
    private final Semaphore downloads = new Semaphore(4);
    public AccountExportDownloadController(AccountExportJobs jobs, ExportSourceClient sources, ObjectMapper mapper) {
        this.jobs = jobs; this.sources = sources; this.mapper = mapper;
    }
    @GetMapping("/api/v1/auth/account/exports/{id}/download")
    public void download(@RequestHeader(value="Authorization",required=false) String authorization, @PathVariable UUID id,
            HttpServletResponse response) throws IOException {
        var job = jobs.requireDownload(authorization, id);
        if (!downloads.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "EXPORT_DOWNLOAD_CAPACITY");
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        try {
            var manifests = new ArrayList<ExportSnapshotStore.Manifest>();
            for (var part : job.parts()) {
                var manifest = sources.manifest(part.source(), id, job.accountId());
                if (!manifest.source().equals(part.source()) || !manifest.jobId().equals(id) || !manifest.accountId().equals(job.accountId())
                        || !manifest.expiresAt().equals(job.expiresAt()) || !manifest.fingerprint().equals(part.fingerprint()))
                    throw new IOException("EXPORT_RECEIPT_INTEGRITY_FAILURE");
                manifests.add(manifest);
            }
            jobs.requireDownload(authorization, id);
            response.setStatus(200); response.setContentType("application/zip");
            response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\"chanter-account-" + id + ".zip\"");
            ExportArchive.write(response.getOutputStream(), manifests, mapper, sources::page, () -> {
                if (System.nanoTime() > deadline) throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "EXPORT_DOWNLOAD_TIMEOUT");
                jobs.requireDownload(authorization, id);
            });
        } catch (IOException failure) {
            if (!response.isCommitted()) { response.reset(); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_SOURCE_UNAVAILABLE"); }
            throw failure;
        } finally { downloads.release(); }
    }
}
