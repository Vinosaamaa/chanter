package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.community.application.CourseService;
import com.chanter.community.domain.Course;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping("/study-servers/{studyServerId}/courses")
    public ResponseEntity<CourseResponse> createCourse(
            @PathVariable UUID studyServerId,
            @Valid @RequestBody CreateCourseRequest request
    ) {
        Course course = courseService.createCourseWithCohort(
                studyServerId,
                request.ownerUserId(),
                request.title(),
                request.instructorUserId(),
                request.cohortName()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(course.id())
                .toUri();

        return ResponseEntity.created(location).body(CourseResponse.from(course));
    }

    @PostMapping("/cohorts/{cohortId}/enrollments")
    public ResponseEntity<Void> enrollLearner(
            @PathVariable UUID cohortId,
            @Valid @RequestBody CreateEnrollmentRequest request
    ) {
        courseService.enrollLearner(cohortId, request.instructorUserId(), request.learnerUserId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri()).build();
    }

    @GetMapping("/course-channels/{channelId}")
    public CourseChannelResponse getCourseChannel(
            @PathVariable UUID channelId,
            @RequestParam UUID viewerUserId
    ) {
        return courseService.findAccessibleChannel(channelId, viewerUserId)
                .map(CourseChannelResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Channel access requires Cohort Enrollment"
                ));
    }

    @GetMapping("/course-channels/{channelId}/support-question-access")
    public SupportQuestionChannelAccessResponse getSupportQuestionChannelAccess(
            @PathVariable UUID channelId,
            @RequestParam UUID userId
    ) {
        return SupportQuestionChannelAccessResponse.from(
                courseService.findSupportQuestionChannelAccess(channelId, userId)
        );
    }

    @GetMapping("/courses/{courseId}/resource-access")
    public CourseResourceAccessResponse getCourseResourceAccess(
            @PathVariable UUID courseId,
            @RequestParam UUID userId
    ) {
        return CourseResourceAccessResponse.from(
                courseService.findCourseResourceAccess(courseId, userId)
        );
    }

    @GetMapping("/cohorts/{cohortId}/ta-queue-access")
    public CohortTaQueueAccessResponse getCohortTaQueueAccess(
            @PathVariable UUID cohortId,
            @RequestParam UUID userId
    ) {
        return CohortTaQueueAccessResponse.from(
                courseService.findCohortTaQueueAccess(cohortId, userId)
        );
    }
}
