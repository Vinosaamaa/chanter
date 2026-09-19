package com.chanter.media.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.InternalEventAccess;
import com.chanter.media.application.CourseResourceService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Trusted worker access is source-version scoped and never substitutes a fabricated viewer identity. */
@RestController
@RequestMapping("/api/v1/internal/resource-ingestion")
public class ResourceIngestionContentController {
    private final CourseResourceService resources;
    private final InternalEventAccess access;
    public ResourceIngestionContentController(CourseResourceService resources,
            @Value("${chanter.internal-service-token}") String token) {
        this.resources = resources; this.access = new InternalEventAccess(token);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id, @RequestParam UUID courseId,
            @RequestParam String sha256, @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String token) {
        access.require(token);
        var stored = resources.downloadForIngestion(id, courseId, sha256);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .contentLength(stored.courseResource().byteSize()).body(new InputStreamResource(stored.content()));
    }
}
