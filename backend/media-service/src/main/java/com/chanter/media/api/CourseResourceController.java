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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
            @RequestPart("file") MultipartFile file
    ) {
        CourseResource courseResource = courseResourceService.uploadCourseResource(
                courseId,
                uploaderUserId,
                title,
                aiApproved,
                file
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{resourceId}")
                .buildAndExpand(courseResource.id())
                .toUri();

        return ResponseEntity.created(location).body(CourseResourceResponse.from(courseResource));
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
    public ResponseEntity<byte[]> downloadCourseResource(
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
                .contentType(MediaType.parseMediaType(stored.courseResource().contentType()))
                .body(stored.content());
    }
}
