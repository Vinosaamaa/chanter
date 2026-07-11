package com.chanter.community.application;

import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.Cohort;
import com.chanter.community.domain.CohortOfficeHoursAccess;
import com.chanter.community.domain.CohortTaQueueAccess;
import com.chanter.community.domain.CohortEnrollment;
import com.chanter.community.domain.CohortEnrollmentList;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseChannelMessageAccess;
import com.chanter.community.domain.CourseResourceAccess;
import com.chanter.community.domain.CourseRole;
import com.chanter.community.domain.InstructorRole;
import com.chanter.community.domain.SupportQuestionChannelAccess;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {

    public static final int MAX_COHORT_ENROLLMENT_PAGE_SIZE = 500;
    public static final int DEFAULT_COHORT_ENROLLMENT_PAGE_SIZE = 50;

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

    public CohortEnrollmentList listCohortEnrollments(
            UUID cohortId,
            UUID instructorUserId,
            int limit,
            int offset
    ) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }
        if (!courseRepository.cohortHasInstructor(cohortId, instructorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Course Instructor can view enrollments");
        }

        int boundedLimit = Math.min(Math.max(limit, 1), MAX_COHORT_ENROLLMENT_PAGE_SIZE);
        int boundedOffset = Math.max(offset, 0);
        return courseRepository.listCohortEnrollments(cohortId, boundedLimit, boundedOffset);
    }

    public Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId) {
        if (!courseRepository.courseChannelExists(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        }

        return courseRepository.findAccessibleChannel(channelId, viewerUserId);
    }

    public SupportQuestionChannelAccess findSupportQuestionChannelAccess(UUID channelId, UUID userId) {
        if (!courseRepository.courseChannelExists(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        }

        return courseRepository.findSupportQuestionChannelAccess(channelId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Channel access requires Cohort Enrollment or Instructor role"
                ));
    }

    public CourseChannelMessageAccess findCourseChannelMessageAccess(UUID channelId, UUID userId) {
        CourseChannel channel = findAccessibleChannel(channelId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Channel access requires Cohort Enrollment or Instructor role"
                ));
        if (channel.kind() != ChannelKind.TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Channel is not a Text Channel");
        }

        return new CourseChannelMessageAccess(
                channel.id(),
                channel.courseId(),
                channel.name(),
                true,
                true
        );
    }

    public CourseResourceAccess findCourseResourceAccess(UUID courseId, UUID userId) {
        if (!courseRepository.courseExists(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        return courseRepository.findCourseResourceAccess(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Resource access requires Cohort Enrollment or Instructor role"
                ));
    }

    public CohortTaQueueAccess findCohortTaQueueAccess(UUID cohortId, UUID userId) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }

        return courseRepository.findCohortTaQueueAccess(cohortId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "TA Queue access requires Cohort Enrollment or Instructor role"
                ));
    }

    public CohortOfficeHoursAccess findCohortOfficeHoursAccess(UUID cohortId, UUID userId) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }

        return courseRepository.findCohortOfficeHoursAccess(cohortId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Office Hours access requires Cohort Enrollment or Instructor role"
                ));
    }
}
