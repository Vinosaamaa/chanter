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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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
        courseService.enrollLearnerByIdentity(
                cohortId,
                instructorUserId,
                request.email(),
                request.learnerUserId()
        );
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri()).build();
    }

    @PostMapping("/cohorts/{cohortId}/channels")
    public ResponseEntity<CourseChannelResponse> createCohortChannel(
            @PathVariable UUID cohortId,
            @Valid @RequestBody CreateCourseChannelRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CourseChannelResponse response = CourseChannelResponse.from(
                courseService.createCohortChannel(cohortId, actorUserId, request.name(), request.kind())
        );
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ServiceInfo.API_V1_PREFIX + "/course-channels/{channelId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/course-channels/{channelId}")
    public CourseChannelResponse renameCohortChannel(
            @PathVariable UUID channelId,
            @Valid @RequestBody UpdateCourseChannelRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        return CourseChannelResponse.from(
                courseService.renameCohortChannel(channelId, actorUserId, request.name())
        );
    }

    @DeleteMapping("/course-channels/{channelId}")
    public ResponseEntity<Void> archiveCohortChannel(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        courseService.archiveCohortChannel(channelId, actorUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/course-channels/{channelId}/voice-presences")
    public ResponseEntity<VoicePresenceResponse> joinCourseVoiceChannel(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID memberUserId
    ) {
        VoicePresenceResponse response = VoicePresenceResponse.from(
                courseService.joinCourseVoiceChannel(channelId, memberUserId)
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{memberUserId}")
                .buildAndExpand(response.memberUserId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/course-channels/{channelId}/voice-presences")
    public VoicePresenceListResponse getCourseVoicePresences(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return VoicePresenceListResponse.from(courseService.findCourseVoicePresences(channelId, viewerUserId));
    }

    @DeleteMapping("/course-channels/{channelId}/voice-presences")
    public ResponseEntity<Void> leaveCourseVoiceChannel(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID memberUserId
    ) {
        courseService.leaveCourseVoiceChannel(channelId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/course-channels/{channelId}/media-token")
    public VoiceMediaTokenResponse issueCourseVoiceMediaToken(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID memberUserId
    ) {
        return VoiceMediaTokenResponse.from(
                courseService.issueCourseVoiceChannelMediaToken(channelId, memberUserId)
        );
    }

    @GetMapping("/cohorts/{cohortId}/roster")
    public CohortRosterResponse getCohortRoster(
            @PathVariable UUID cohortId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return CohortRosterResponse.from(courseService.getCohortRoster(cohortId, viewerUserId));
    }

    @PostMapping("/cohorts/{cohortId}/teaching-assistants/{userId}")
    public ResponseEntity<Void> addTeachingAssistant(
            @PathVariable UUID cohortId,
            @PathVariable UUID userId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        courseService.addTeachingAssistant(cohortId, actorUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cohorts/{cohortId}/teaching-assistants/{userId}")
    public ResponseEntity<Void> removeTeachingAssistant(
            @PathVariable UUID cohortId,
            @PathVariable UUID userId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        courseService.removeTeachingAssistant(cohortId, actorUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cohorts/{cohortId}/invitations")
    public ResponseEntity<CohortInvitationResponse> createCohortInvitation(
            @PathVariable UUID cohortId,
            @Valid @RequestBody CreateCohortInvitationRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        CohortInvitationResponse response = CohortInvitationResponse.from(
                courseService.createCohortInvitation(cohortId, actorUserId, request.email())
        );
        return ResponseEntity.created(
                        ServletUriComponentsBuilder.fromCurrentRequest()
                                .path("/{invitationId}")
                                .buildAndExpand(response.id())
                                .toUri()
                )
                .body(response);
    }

    @DeleteMapping("/cohorts/{cohortId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancelCohortInvitation(
            @PathVariable UUID cohortId,
            @PathVariable UUID invitationId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        courseService.cancelCohortInvitation(cohortId, actorUserId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cohorts/{cohortId}/enrollments/{learnerUserId}")
    public ResponseEntity<Void> removeEnrollment(
            @PathVariable UUID cohortId,
            @PathVariable UUID learnerUserId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        courseService.removeEnrollment(cohortId, actorUserId, learnerUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/cohorts/{cohortId}/enrollments/teaching-assistant")
    public ResponseEntity<Void> assignTeachingAssistant(
            @PathVariable UUID cohortId,
            @Valid @RequestBody AssignTeachingAssistantRequest request,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
    ) {
        courseService.assignTeachingAssistant(
                cohortId,
                actorUserId,
                request.learnerUserIds(),
                request.teachingAssistantUserId()
        );
        return ResponseEntity.noContent().build();
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
