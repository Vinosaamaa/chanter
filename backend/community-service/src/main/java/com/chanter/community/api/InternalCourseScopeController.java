package com.chanter.community.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.InternalEventAccess;
import com.chanter.community.application.CourseRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Authoritative course scope for service-owned indexing; this is not a viewer permission grant. */
@RestController
@RequestMapping("/api/v1/internal/course-scope")
public class InternalCourseScopeController {
    private final CourseRepository courses;
    private final InternalEventAccess access;
    private final com.chanter.common.lifecycle.SourceDeletionRequests deletions;
    public InternalCourseScopeController(CourseRepository courses,com.chanter.common.lifecycle.SourceDeletionRequests deletions,
            @Value("${chanter.internal-service-token}") String token) {
        this.courses = courses; this.access = new InternalEventAccess(token); this.deletions=deletions;
    }
    @GetMapping("/{courseId}")
    public Scope scope(@PathVariable UUID courseId,
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String token) {
        access.require(token);
        UUID server=courses.findStudyServerIdByCourseId(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        deletions.requireOpen(server);
        return new Scope(courseId,server);
    }
    public record Scope(UUID courseId, UUID studyServerId) {}
}
