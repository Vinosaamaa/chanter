package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
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
import org.springframework.web.bind.annotation.RequestAttribute;
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
            @Valid @RequestBody CreateCourseRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        Course course = courseService.createCourseWithCohort(
                studyServerId,
                actorUserId,
                request.title(),
                actorUserId,
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
            @Valid @RequestBody CreateEnrollmentRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID instructorUserId
    ) {
        courseService.enrollLearner(cohortId, instructorUserId, request.learnerUserId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri()).build();
    }

    @PostMapping("/cohorts/{cohortId}/join")
    public ResponseEntity<Void> joinCohort(
            @PathVariable UUID cohortId,
            @Valid @RequestBody CreateJoinCohortRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID learnerUserId
    ) {
        courseService.joinCohort(cohortId, learnerUserId, request.inviteCode());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cohorts/{cohortId}/invite")
    public CohortInviteResponse getCohortInvite(
            @PathVariable UUID cohortId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID instructorUserId
    ) {
        UUID inviteCode = courseService.getCohortInviteCode(cohortId, instructorUserId);
        return new CohortInviteResponse(cohortId, inviteCode);
    }

    @GetMapping("/cohorts/{cohortId}/enrollments")
    public CohortEnrollmentListResponse listCohortEnrollments(
            @PathVariable UUID cohortId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String search,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID instructorUserId
    ) {
        var page = courseService.listCohortEnrollments(cohortId, instructorUserId, limit, offset, search);
        return new CohortEnrollmentListResponse(
                page.enrollments().stream().map(CohortEnrollmentResponse::from).toList(),
                page.totalCount(),
                page.limit(),
                page.offset()
        );
    }

    @GetMapping("/course-channels/{channelId}")
    public CourseChannelResponse getCourseChannel(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
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
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return SupportQuestionChannelAccessResponse.from(
                courseService.findSupportQuestionChannelAccess(channelId, userId)
        );
    }

    @GetMapping("/course-channels/{channelId}/channel-message-access")
    public CourseChannelMessageAccessResponse getCourseChannelMessageAccess(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return CourseChannelMessageAccessResponse.from(
                courseService.findCourseChannelMessageAccess(channelId, userId)
        );
    }

    @GetMapping("/courses/{courseId}/resource-access")
    public CourseResourceAccessResponse getCourseResourceAccess(
            @PathVariable UUID courseId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return CourseResourceAccessResponse.from(
                courseService.findCourseResourceAccess(courseId, userId)
        );
    }

    @GetMapping("/cohorts/{cohortId}/ta-queue-access")
    public CohortTaQueueAccessResponse getCohortTaQueueAccess(
            @PathVariable UUID cohortId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return CohortTaQueueAccessResponse.from(
                courseService.findCohortTaQueueAccess(cohortId, userId)
        );
    }

    @GetMapping("/cohorts/{cohortId}/office-hours-access")
    public CohortOfficeHoursAccessResponse getCohortOfficeHoursAccess(
            @PathVariable UUID cohortId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return CohortOfficeHoursAccessResponse.from(
                courseService.findCohortOfficeHoursAccess(cohortId, userId)
        );
    }
}
