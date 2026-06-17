# Issue 13 Change Log: Create Course, Cohort, And Enroll Learner

Date: 2026-06-17  
Branch: `feature/13-create-course-cohort-enroll-learner`  
Issue: `#13 Slice: Create Course, Cohort, And Enroll Learner`  
Commit status: in progress on feature branch.

## Acceptance Criteria Covered

- Owner can create a Course and Cohort inside a Study Server.
- Instructor can enroll a learner in the Cohort.
- Non-Instructor users cannot enroll learners in the Cohort.
- Enrolled learner can access Course Channels.
- Non-enrolled user cannot access Course Channels.
- Tests cover the Course Channel access boundary.

## 1. Added TDD Smoke Test For Enrollment Boundary

File:

- `backend/community-service/src/test/java/com/chanter/community/api/CourseEnrollmentSmokeTest.java`

What changed:

- Added a Spring MVC test that exercises the public HTTP API.
- Creates a Study Server through the existing #12 endpoint.
- Creates a Course, Cohort, Instructor role, and default Course Channels.
- Enrolls a learner and verifies the enrolled learner can read a Course Channel while a non-enrolled user receives `403`.
- Verifies a non-Instructor receives `403` when trying to enroll a learner.

Snippet:

```java
MvcResult courseResult = mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "ownerUserId", ownerUserId.toString(),
                        "title", "Spring Boot Foundations",
                        "instructorUserId", instructorUserId.toString(),
                        "cohortName", "Summer 2026"
                ))))
        .andExpect(status().isCreated())
        .andReturn();
```

Snippet:

```java
mockMvc.perform(get("/api/v1/course-channels/{channelId}", course.channels().getFirst().id())
                .param("viewerUserId", nonEnrolledUserId.toString()))
        .andExpect(status().isForbidden());
```

## 2. Added Course, Cohort, Role, Channel, And Enrollment Domain

Files:

- `backend/community-service/src/main/java/com/chanter/community/domain/Course.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/Cohort.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/CourseChannel.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/CourseRole.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/InstructorRole.java`

What changed:

- Modeled Course as belonging to a Study Server.
- Modeled Cohort as belonging to a Course.
- Modeled Course Channels separately from Study Server Channels.
- Added an Instructor role at Course scope.

Snippet:

```java
public record Course(
        UUID id,
        UUID studyServerId,
        String title,
        InstructorRole instructorRole,
        Cohort cohort,
        List<CourseChannel> channels,
        Instant createdAt
) {
}
```

## 3. Added Course Application Service And Repository Boundary

Files:

- `backend/community-service/src/main/java/com/chanter/community/application/CourseService.java`
- `backend/community-service/src/main/java/com/chanter/community/application/CourseRepository.java`

What changed:

- Added application service methods to create a Course with a Cohort, enroll a learner, and read an accessible Course Channel.
- Enforced Study Server Owner authorization for Course creation.
- Enforced Course Instructor authorization for Enrollment.

Snippet:

```java
if (!studyServer.ownerRole().userId().equals(ownerUserId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Study Server Owner can create Courses");
}
```

Snippet:

```java
if (!courseRepository.cohortHasInstructor(cohortId, instructorUserId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Course Instructor can enroll learners");
}
```

## 4. Added Course API

Files:

- `backend/community-service/src/main/java/com/chanter/community/api/CourseController.java`
- `backend/community-service/src/main/java/com/chanter/community/api/CreateCourseRequest.java`
- `backend/community-service/src/main/java/com/chanter/community/api/CreateEnrollmentRequest.java`
- `backend/community-service/src/main/java/com/chanter/community/api/CourseResponse.java`
- `backend/community-service/src/main/java/com/chanter/community/api/CourseChannelResponse.java`

What changed:

- Added `POST /api/v1/study-servers/{studyServerId}/courses`.
- Added `POST /api/v1/cohorts/{cohortId}/enrollments`.
- Added `GET /api/v1/course-channels/{channelId}?viewerUserId=...`.

Snippet:

```java
@PostMapping("/cohorts/{cohortId}/enrollments")
public ResponseEntity<Void> enrollLearner(
        @PathVariable UUID cohortId,
        @Valid @RequestBody CreateEnrollmentRequest request
) {
    courseService.enrollLearner(cohortId, request.instructorUserId(), request.learnerUserId());
    return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri()).build();
}
```

## 5. Added Persistence And Migration

Files:

- `backend/community-service/src/main/resources/db/migration/V2__create_course_enrollment_tables.sql`
- `backend/community-service/src/main/java/com/chanter/community/infra/JdbcCourseRepository.java`

What changed:

- Added tables for Courses, Course roles, Cohorts, Course Channels, and Cohort Enrollments.
- Added JDBC persistence for Course creation and Enrollment.
- Added Course Channel access lookup through either Course Instructor role or Cohort Enrollment.

Snippet:

```sql
CREATE TABLE cohort_enrollments (
    cohort_id UUID NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    learner_user_id UUID NOT NULL,
    enrolled_by_user_id UUID NOT NULL,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (cohort_id, learner_user_id)
);
```

Snippet:

```java
SELECT DISTINCT cc.id, cc.course_id, cc.name, cc.kind, cc.position
FROM course_channels cc
LEFT JOIN course_roles cr ON cr.course_id = cc.course_id
LEFT JOIN cohorts c ON c.course_id = cc.course_id
LEFT JOIN cohort_enrollments ce ON ce.cohort_id = c.id
WHERE cc.id = :channelId
AND (
    (
        cr.user_id = :viewerUserId
        AND cr.role = :instructorRole
    )
    OR ce.learner_user_id = :viewerUserId
)
```

## 6. Wired Gateway And Frontend Manual Flow

Files:

- `backend/gateway-service/src/main/resources/application.yml`
- `frontend/src/App.tsx`
- `frontend/src/App.css`

What changed:

- Gateway now forwards Study Server, Cohort, and Course Channel API paths to `community-service`.
- Frontend shell can create a Course and Cohort after creating a Study Server.
- Frontend shell can enroll a learner and show the access check result.

Snippet:

```yaml
- Path=/api/v1/study-servers/**,/api/v1/cohorts/**,/api/v1/course-channels/**
```

Snippet:

```typescript
const learnerAccess = await fetch(
  `/api/v1/course-channels/${firstChannel.id}?viewerUserId=${learnerUserId}`,
)
const outsiderAccess = await fetch(
  `/api/v1/course-channels/${firstChannel.id}?viewerUserId=${nonEnrolledUserId}`,
)
```

## Verification

Red:

- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service test`
  - Failed first with `POST /api/v1/study-servers/{id}/courses` returning `404`.
  - Failed next with Instructor Course Channel access returning `403`.

Green:

- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service test` — pass.
- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn verify` — pass.
- `npm run lint` — pass.
- `npm run build` — pass.
