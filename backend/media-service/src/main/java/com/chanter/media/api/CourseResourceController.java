package com.chanter.media.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.media.application.CourseResourceService;
import com.chanter.media.domain.CourseResource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class CourseResourceController {

    private final CourseResourceService courseResourceService;

    public CourseResourceController(CourseResourceService courseResourceService) {
        this.courseResourceService = courseResourceService;
    }

    @PostMapping(value = "/courses/{courseId}/course-resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseResourceResponse> uploadCourseResource(
            @PathVariable UUID courseId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID uploaderUserId,
            @RequestParam(required = false) String title,
            @RequestParam boolean aiApproved,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @RequestHeader(value = "X-Content-SHA256", required = false) String checksum
    ) {
        CourseResource courseResource = courseResourceService.uploadCourseResource(
                courseId,
                uploaderUserId,
                title,
                aiApproved,
                file, idempotencyKey, checksum
        );
        URI location = URI.create(ServiceInfo.API_V1_PREFIX + "/course-resources/" + courseResource.id());

        return ResponseEntity.accepted().location(location).body(CourseResourceResponse.from(courseResource));
    }

    @GetMapping("/courses/{courseId}/course-resources")
    public CourseResourceListResponse listCourseResources(
            @PathVariable UUID courseId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        List<CourseResourceResponse> courseResources = courseResourceService
                .listCourseResources(courseId, viewerUserId)
                .stream()
                .map(CourseResourceResponse::from)
                .toList();

        return new CourseResourceListResponse(courseResources);
    }

    @GetMapping("/course-resources/{resourceId}/content")
    public ResponseEntity<Resource> downloadCourseResource(
            @PathVariable UUID resourceId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        CourseResourceService.StoredCourseResourceContent stored = courseResourceService.downloadCourseResource(
                resourceId,
                viewerUserId
        );

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(stored.courseResource().fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(safeContentType(stored.courseResource().contentType()))
                .contentLength(stored.courseResource().byteSize())
                .body(new InputStreamResource(stored.content()));
    }

    @GetMapping("/course-resources/{resourceId}")
    public CourseResourceResponse resource(@PathVariable UUID resourceId, @RequestAttribute(AuthRequestAttributes.USER_ID) UUID user) {
        return CourseResourceResponse.from(courseResourceService.getCourseResource(resourceId, user));
    }

    @DeleteMapping("/course-resources/{resourceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID resourceId, @RequestAttribute(AuthRequestAttributes.USER_ID) UUID user) {
        courseResourceService.deleteCourseResource(resourceId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses/{courseId}/course-resources/usage")
    public com.chanter.media.application.ResourceLifecycle.Usage usage(@PathVariable UUID courseId, @RequestAttribute(AuthRequestAttributes.USER_ID) UUID user) {
        return courseResourceService.usage(courseId, user);
    }

    /** Parse stored content type for download; fall back if missing/invalid (SEC-17). */
    static MediaType safeContentType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(rawContentType);
        } catch (Exception exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
