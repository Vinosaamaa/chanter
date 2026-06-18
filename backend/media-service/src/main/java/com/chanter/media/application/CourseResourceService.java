package com.chanter.media.application;

import com.chanter.media.domain.CourseResource;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseResourceService {

    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;

    private final CourseResourceRepository repository;
    private final CourseResourceAccessClient accessClient;
    private final LocalCourseResourceStorage storage;
    private final Clock clock;

    public CourseResourceService(
            CourseResourceRepository repository,
            CourseResourceAccessClient accessClient,
            LocalCourseResourceStorage storage,
            Clock clock
    ) {
        this.repository = repository;
        this.accessClient = accessClient;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public CourseResource uploadCourseResource(
            UUID courseId,
            UUID uploaderUserId,
            String title,
            boolean aiApproved,
            MultipartFile file
    ) {
        CourseResourceAccess access = accessClient.requireAccess(courseId, uploaderUserId);
        if (!access.canUploadCourseResource()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Course Instructors can upload Course Resources");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource file must not be empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource file exceeds the 10 MB limit");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource file name must not be blank");
        }

        String normalizedTitle = title == null || title.isBlank() ? fileName.trim() : title.trim();
        if (normalizedTitle.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource title must not be blank");
        }

        UUID resourceId = UUID.randomUUID();
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded Course Resource");
        }

        try {
            storage.store(resourceId, content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store Course Resource");
        }

        CourseResource courseResource = new CourseResource(
                resourceId,
                courseId,
                normalizedTitle,
                fileName.trim(),
                contentType,
                content.length,
                resourceId.toString(),
                aiApproved,
                uploaderUserId,
                clock.instant()
        );

        return repository.save(courseResource);
    }

    @Transactional(readOnly = true)
    public List<CourseResource> listCourseResources(UUID courseId, UUID viewerUserId) {
        CourseResourceAccess access = accessClient.requireAccess(courseId, viewerUserId);
        if (!access.canViewCourseResources()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Course Resource access requires Cohort Enrollment or Instructor role"
            );
        }

        return repository.findByCourseId(courseId);
    }

    @Transactional(readOnly = true)
    public StoredCourseResourceContent downloadCourseResource(UUID resourceId, UUID viewerUserId) {
        CourseResource courseResource = repository.findById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource not found"));

        CourseResourceAccess access = accessClient.requireAccess(courseResource.courseId(), viewerUserId);
        if (!access.canViewCourseResources()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Course Resource access requires Cohort Enrollment or Instructor role"
            );
        }

        byte[] content;
        try {
            content = storage.load(resourceId);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource content is unavailable");
        }

        return new StoredCourseResourceContent(courseResource, content);
    }

    public record StoredCourseResourceContent(CourseResource courseResource, byte[] content) {
    }
}
