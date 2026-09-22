package com.chanter.auth.lifecycle;

import com.chanter.common.lifecycle.ExportArchive;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ExportDownloadHandles handles;
    private final com.chanter.auth.api.BrowserSessionCookies cookies;
    private final Semaphore downloads = new Semaphore(4);
    public AccountExportDownloadController(AccountExportJobs jobs, ExportSourceClient sources, ObjectMapper mapper,
            ExportDownloadHandles handles, com.chanter.auth.api.BrowserSessionCookies cookies) {
        this.jobs = jobs; this.sources = sources; this.mapper = mapper; this.handles = handles; this.cookies = cookies;
    }
    @PostMapping("/api/v1/auth/account/exports/{id}/download-authorization")
    public org.springframework.http.ResponseEntity<Void> authorize(@RequestHeader(value="Authorization",required=false) String authorization,
            @PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        var issued = handles.issue(authorization, id, cookies.read(request, com.chanter.auth.api.BrowserSessionCookies.REFRESH_COOKIE));
        writeCookie(response, id, issued.handle(), Math.max(0, java.time.Duration.between(java.time.Instant.now(), issued.expiresAt()).toSeconds()));
        return org.springframework.http.ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
    @GetMapping("/api/v1/auth/account/exports/{id}/download")
    public void download(@RequestHeader(value="Authorization",required=false) String authorization, @PathVariable UUID id,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        java.util.function.Supplier<AccountExportJobs.Job> current;
        boolean navigation = authorization == null || authorization.isBlank();
        if (!navigation) current = () -> jobs.requireDownload(authorization, id);
        else {
            var grant = handles.consume(id, cookies.read(request, ExportDownloadHandles.COOKIE),
                    cookies.read(request, com.chanter.auth.api.BrowserSessionCookies.REFRESH_COOKIE));
            writeCookie(response, id, "", 0);
            current = () -> jobs.requireDownload(grant.accountId(), grant.sessionId(), grant.accessExpiresAt(), id);
        }
        var job = current.get();
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
            current.get();
            response.setStatus(200); response.setContentType("application/zip");
            response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\"chanter-account-" + id + ".zip\"");
            ExportArchive.write(response.getOutputStream(), manifests, mapper, sources::page, () -> {
                if (System.nanoTime() > deadline) throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "EXPORT_DOWNLOAD_TIMEOUT");
                current.get();
            });
        } catch (IOException failure) {
            if (!response.isCommitted()) {
                response.reset();
                if (navigation) writeCookie(response, id, "", 0);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_SOURCE_UNAVAILABLE");
            }
            throw failure;
        } finally { downloads.release(); }
    }
    private static void writeCookie(HttpServletResponse response, UUID id, String value, long seconds) {
        response.addHeader("Set-Cookie", org.springframework.http.ResponseCookie.from(ExportDownloadHandles.COOKIE, value)
                .secure(true).httpOnly(true).sameSite("Strict").path(ExportDownloadHandles.path(id)).maxAge(seconds).build().toString());
        response.setHeader("Cache-Control", "no-store");
    }
}
