package com.chanter.media.application;

import com.chanter.media.domain.CourseResource;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseResourceService {

    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;

    /** Allowed upload MIME types (SEC-17). Parameters (e.g. charset) are stripped before compare. */
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "audio/mpeg",
            "audio/mp4",
            "audio/wav",
            "audio/ogg",
            "audio/webm",
            "video/mp4",
            "video/webm",
            "video/quicktime"
    );

    private final CourseResourceRepository repository;
    private final CourseResourceAccessClient accessClient;
    private final LocalCourseResourceStorage storage;
    private final ResourceIngestionClient resourceIngestionClient;
    private final Clock clock;

    public CourseResourceService(
            CourseResourceRepository repository,
            CourseResourceAccessClient accessClient,
            LocalCourseResourceStorage storage,
            ResourceIngestionClient resourceIngestionClient,
            Clock clock
    ) {
        this.repository = repository;
        this.accessClient = accessClient;
        this.storage = storage;
        this.resourceIngestionClient = resourceIngestionClient;
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

        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (fileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource file name must not be blank");
        }

        String normalizedTitle = title == null || title.isBlank() ? fileName : title.trim();
        if (normalizedTitle.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource title must not be blank");
        }

        UUID resourceId = UUID.randomUUID();
        String contentType = requireAllowedContentType(file.getContentType());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded Course Resource");
        }

        CourseResource courseResource = new CourseResource(
                resourceId,
                courseId,
                normalizedTitle,
                fileName,
                contentType,
                content.length,
                resourceId.toString(),
                aiApproved,
                uploaderUserId,
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        );

        repository.save(courseResource);

        try {
            storage.store(resourceId, content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store Course Resource");
        }

        if (aiApproved) {
            resourceIngestionClient.ingestAiApprovedResource(courseId, resourceId, fileName, content);
        }

        return courseResource;
    }

    static String sanitizeFileName(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }

        String normalized = originalFileName.replace('\\', '/').trim();
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }

        if (normalized.equals(".") || normalized.equals("..")) {
            return "";
        }

        return normalized;
    }

    static String requireAllowedContentType(String rawContentType) {
        String normalized = normalizeContentType(rawContentType);
        if (normalized == null || !ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Course Resource content type is not allowed"
            );
        }
        return normalized;
    }

    /** Strip parameters and lowercase type/subtype; null/blank → null. */
    static String normalizeContentType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return null;
        }
        String withoutParams = rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (withoutParams.isEmpty() || withoutParams.indexOf('/') < 1) {
            return null;
        }
        return withoutParams;
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Course Resource content is unavailable");
        }

        return new StoredCourseResourceContent(courseResource, content);
    }

    public record StoredCourseResourceContent(CourseResource courseResource, byte[] content) {
    }
}
