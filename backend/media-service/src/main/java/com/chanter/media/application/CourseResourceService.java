package com.chanter.media.application;

import com.chanter.media.domain.CourseResource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseResourceService {
    private final ResourceLifecycle lifecycle;
    private final CourseResourceAccessClient accessClient;
    private final PrivateResourceStorage storage;
    private final UploadValidator validator;
    private final Clock clock;
    private final Semaphore transfers = new Semaphore(2);

    public CourseResourceService(ResourceLifecycle lifecycle, CourseResourceAccessClient accessClient,
            PrivateResourceStorage storage, UploadValidator validator, Clock clock) {
        this.lifecycle = lifecycle; this.accessClient = accessClient; this.storage = storage; this.validator = validator; this.clock = clock;
    }

    public CourseResource uploadCourseResource(UUID courseId, UUID userId, String title, boolean aiApproved,
                                               MultipartFile file, UUID idempotencyKey, String checksum) {
        requireUpload(courseId, userId);
        UUID studyServerId = accessClient.requireStudyServerId(courseId);
        acquire();
        try (var upload = validator.validate(file, checksum)) {
            String normalizedTitle = title == null || title.isBlank() ? upload.fileName() : title.strip();
            if (normalizedTitle.length() > 255 || normalizedTitle.chars().anyMatch(Character::isISOControl)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Resource title is invalid");
            }
            UUID id = UUID.randomUUID();
            var candidate = new CourseResource(id, courseId, normalizedTitle, upload.fileName(), upload.contentType(), upload.byteSize(),
                    PrivateResourceStorage.PREFIX + courseId + "/" + id + "/" + UUID.randomUUID(), aiApproved, userId,
                    clock.instant().truncatedTo(ChronoUnit.MICROS), "STAGING", upload.sha256(),
                    idempotencyKey == null ? UUID.randomUUID() : idempotencyKey, storage.backend(), "NONE", java.util.Set.of(), studyServerId, null);
            CourseResource reserved = lifecycle.reserve(candidate);
            if (!reserved.id().equals(id)) return reserved;
            try {
                storage.put(candidate.storageKey(), upload.path(), upload.sha256());
                lifecycle.quarantine(id);
            } catch (Exception unavailable) {
                // A timed-out put may have succeeded. Keep the reservation until a worker confirms deletion.
                lifecycle.requestDelete(id);
                if (unavailable instanceof ResponseStatusException budget) throw budget;
            }
            return lifecycle.find(id).orElseThrow();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Course Resource processing is unavailable");
        } finally { transfers.release(); }
    }

    public List<CourseResource> listCourseResources(UUID course, UUID user) {
        var access = requireView(course, user);
        return lifecycle.list(course, access.canUploadCourseResource());
    }

    public CourseResource getCourseResource(UUID id, UUID user) {
        var resource = existing(id);
        var access = requireView(resource.courseId(), user);
        if (!access.canUploadCourseResource() && !resource.state().equals("AVAILABLE")) throw missing();
        return resource;
    }

    public void deleteCourseResource(UUID id, UUID user) {
        var resource = lifecycle.find(id).orElseThrow(CourseResourceService::missing);
        requireUpload(resource.courseId(), user);
        lifecycle.requestDelete(id);
    }

    public ResourceLifecycle.Usage usage(UUID course, UUID user) {
        requireUpload(course, user);
        return lifecycle.courseUsage(course);
    }

    public CourseResource retryIngestion(UUID id, UUID user) {
        var resource = existing(id);
        requireUpload(resource.courseId(), user);
        lifecycle.retryIndex(id);
        return existing(id);
    }

    public StoredCourseResourceContent downloadCourseResource(UUID id, UUID user) {
        var resource = existing(id);
        requireView(resource.courseId(), user);
        return download(resource, current -> true);
    }

    public CourseResource setAiApproved(UUID id, UUID user, boolean approved) {
        var resource = existing(id);
        requireUpload(resource.courseId(), user);
        lifecycle.setAiApproved(id, approved);
        return existing(id);
    }

    public StoredCourseResourceContent downloadForIngestion(UUID id, UUID course, String sha256) {
        var resource = existing(id);
        java.util.function.Predicate<CourseResource> matches = current -> current.aiApproved()
                && current.courseId().equals(course) && current.sha256().equals(sha256);
        if (!matches.test(resource)) throw missing();
        return download(resource, matches);
    }

    private StoredCourseResourceContent download(CourseResource resource,
            java.util.function.Predicate<CourseResource> stillAuthorized) {
        UUID id = resource.id();
        if (!resource.state().equals("AVAILABLE")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Course Resource is not available");
        if (!resource.storageBackend().equals(storage.backend())) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Course Resource migration is required");
        acquire();
        java.nio.file.Path temporary = null;
        try {
            var path = validator.verifiedDownload(storage.open(resource.storageKey()), resource.byteSize(), resource.sha256());
            temporary = path;
            // Deletion accepted during a provider read must win before any bytes are exposed.
            var latest = lifecycle.find(id);
            if (latest.isEmpty() || !latest.get().state().equals("AVAILABLE") || !stillAuthorized.test(latest.get())) {
                Files.deleteIfExists(path); throw missing();
            }
            var closed = new AtomicBoolean();
            InputStream content = new FilterInputStream(Files.newInputStream(path)) {
                @Override public void close() throws IOException {
                    if (closed.compareAndSet(false, true)) {
                        try { super.close(); } finally { try { Files.deleteIfExists(path); } finally { transfers.release(); } }
                    }
                }
            };
            return new StoredCourseResourceContent(resource, content);
        } catch (Exception unavailable) {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            transfers.release();
            if (unavailable instanceof ResponseStatusException status) throw status;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Course Resource content is unavailable");
        }
    }

    private CourseResource existing(UUID id) {
        return lifecycle.find(id).filter(resource -> !List.of("DELETE_PENDING", "DELETED").contains(resource.state()))
                .orElseThrow(CourseResourceService::missing);
    }
    private CourseResourceAccess requireView(UUID course, UUID user) {
        var access = accessClient.requireAccess(course, user);
        if (!access.canViewCourseResources()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course Resource access requires enrollment or Instructor role");
        return access;
    }
    private void requireUpload(UUID course, UUID user) {
        if (!accessClient.requireAccess(course, user).canUploadCourseResource()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Course Instructors can manage Course Resources");
    }
    private void acquire() {
        if (!transfers.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Course Resource transfers are busy; retry later");
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Resource not found"); }
    public record StoredCourseResourceContent(CourseResource courseResource, InputStream content) { }
}
