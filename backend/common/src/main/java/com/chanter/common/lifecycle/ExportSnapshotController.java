package com.chanter.common.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ExportSnapshotController {
    private final ExportSnapshotStore snapshots;
    private final InternalLifecycleAccess access;
    private final ExportSnapshotAccess contentAccess;
    private final ExportSourceExecution execution;
    public ExportSnapshotController(ExportSnapshotStore snapshots, @Value("${chanter.internal-service-token}") String token,
            ObjectProvider<ExportSnapshotAccess> contentAccess, ExportSourceExecution execution) {
        this.snapshots = snapshots; this.access = new InternalLifecycleAccess(token);
        this.execution = execution;
        this.contentAccess = contentAccess.getIfAvailable(ExportSnapshotAccess::denyProtected);
    }

    @GetMapping("/api/v1/internal/lifecycle/exports/{jobId}")
    public ResponseEntity<ExportSnapshotStore.Manifest> manifest(@PathVariable UUID jobId, @RequestParam UUID accountId,
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        return execution.read(() -> {
        snapshots.requireAccess(jobId, accountId, null, contentAccess);
        var manifest = snapshots.manifest(jobId, accountId);
        snapshots.requireAccess(jobId, accountId, null, contentAccess);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(manifest);
        });
    }

    @GetMapping("/api/v1/internal/lifecycle/exports/{jobId}/entries/{entry}/pages/{page}")
    public ResponseEntity<byte[]> page(@PathVariable UUID jobId, @PathVariable int entry, @PathVariable int page, @RequestParam UUID accountId,
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        return execution.read(() -> {
        snapshots.requireAccess(jobId, accountId, entry, contentAccess);
        byte[] bytes = snapshots.page(jobId, accountId, entry, page);
        snapshots.requireAccess(jobId, accountId, entry, contentAccess);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff").body(bytes);
        });
    }
}
