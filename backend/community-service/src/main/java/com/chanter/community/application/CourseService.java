package com.chanter.community.application;

import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.Cohort;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseRole;
import com.chanter.community.domain.InstructorRole;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {

    private final StudyServerRepository studyServerRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public CourseService(
            StudyServerRepository studyServerRepository,
            CourseRepository courseRepository,
            Clock clock
    ) {
        this.studyServerRepository = studyServerRepository;
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    public Course createCourseWithCohort(
            UUID studyServerId,
            UUID ownerUserId,
            String title,
            UUID instructorUserId,
            String cohortName
    ) {
        var studyServer = studyServerRepository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
        if (!studyServer.ownerRole().userId().equals(ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Study Server Owner can create Courses");
        }

        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                courseId,
                studyServerId,
                title.trim(),
                new InstructorRole(instructorUserId, CourseRole.INSTRUCTOR),
                new Cohort(UUID.randomUUID(), courseId, cohortName.trim()),
                List.of(
                        new CourseChannel(UUID.randomUUID(), courseId, "announcements", ChannelKind.TEXT, 0),
                        new CourseChannel(UUID.randomUUID(), courseId, "questions", ChannelKind.TEXT, 1),
                        new CourseChannel(UUID.randomUUID(), courseId, "resources", ChannelKind.TEXT, 2)
                ),
                clock.instant()
        );

        return courseRepository.save(course);
    }

    public void enrollLearner(UUID cohortId, UUID instructorUserId, UUID learnerUserId) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }
        if (!courseRepository.cohortHasInstructor(cohortId, instructorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Course Instructor can enroll learners");
        }

        courseRepository.enrollLearner(cohortId, learnerUserId, instructorUserId, clock.instant());
    }

    public Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId) {
        if (!courseRepository.courseChannelExists(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        }

        return courseRepository.findAccessibleChannel(channelId, viewerUserId);
    }
}
