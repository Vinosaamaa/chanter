package com.chanter.media.infra;

import com.chanter.media.application.CourseResourceAccess;
import com.chanter.media.application.CourseResourceAccessClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestCourseResourceAccessClient implements CourseResourceAccessClient {

    private final Map<String, CourseResourceAccess> accessRules = new ConcurrentHashMap<>();
    private final Set<UUID> existingCourses = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> studyServers = new ConcurrentHashMap<>();

    public void registerCourse(UUID courseId) {
        existingCourses.add(courseId);
        studyServers.computeIfAbsent(courseId, ignored -> UUID.randomUUID());
    }

    public void grantInstructorUpload(UUID courseId, UUID userId) {
        registerCourse(courseId);
        accessRules.put(key(courseId, userId), new CourseResourceAccess(courseId, true, true));
    }

    public void grantLearnerView(UUID courseId, UUID userId) {
        registerCourse(courseId);
        accessRules.put(key(courseId, userId), new CourseResourceAccess(courseId, false, true));
    }

    public void clear() {
        accessRules.clear();
        existingCourses.clear();
        studyServers.clear();
    }

    @Override
    public CourseResourceAccess requireAccess(UUID courseId, UUID userId) {
        CourseResourceAccess access = accessRules.get(key(courseId, userId));
        if (access != null) {
            return access;
        }
        if (!existingCourses.contains(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Course Resource access requires Cohort Enrollment or Instructor role"
        );
    }

    private static String key(UUID courseId, UUID userId) {
        return courseId + ":" + userId;
    }
    @Override public UUID requireStudyServerId(UUID courseId) {
        UUID server = studyServers.get(courseId);
        if (server == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        return server;
    }
}
