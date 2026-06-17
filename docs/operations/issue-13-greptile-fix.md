# Issue 13 Greptile Fix Log: Create Course, Cohort, And Enroll Learner

Date: 2026-06-17  
Branch: `feature/13-create-course-cohort-enroll-learner`  
PR: https://github.com/Vinosaamaa/chanter/pull/28  
Final Greptile confidence: `5/5`

## Summary

Greptile reviewed the Course/Cohort/Enrollment slice and initially scored it `4/5`. The actionable P1 feedback was that missing Cohorts and missing Course Channels were being reported as authorization failures. I fixed those by separating existence checks from authorization checks, then re-ran Greptile until the summary updated to `5/5` with zero unresolved threads.

## Fixes Applied

### 1. Nonexistent Cohorts Return 404 Before Instructor Authorization

Greptile finding:

- `cohortHasInstructor` returned `false` both when the Cohort did not exist and when the user was not an Instructor.
- The service converted both cases to `403 Forbidden`, which misled callers when the UUID was simply wrong.

Fix:

- Added `cohortExists(UUID cohortId)` to the repository.
- Checked Cohort existence before Instructor authorization in `CourseService.enrollLearner`.
- Added a smoke-test assertion for `404 Not Found` on unknown Cohort enrollment.

Representative service snippet:

```java
if (!courseRepository.cohortExists(cohortId)) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
}
if (!courseRepository.cohortHasInstructor(cohortId, instructorUserId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Course Instructor can enroll learners");
}
```

Representative repository snippet:

```java
public boolean cohortExists(UUID cohortId) {
    return jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM cohorts
                    WHERE id = :cohortId
                    """)
            .param("cohortId", cohortId)
            .query(Integer.class)
            .single() > 0;
}
```

### 2. Nonexistent Course Channels Return 404 Before Access Authorization

Greptile finding:

- Course Channel lookup returned an empty `Optional` both when a channel did not exist and when the viewer had no access.
- The controller converted both cases to `403 Forbidden`.

Fix:

- Added `courseChannelExists(UUID channelId)` to the repository.
- Checked Course Channel existence before access authorization in `CourseService.findAccessibleChannel`.
- Added a smoke-test assertion for `404 Not Found` on unknown Course Channel lookup.

Representative service snippet:

```java
if (!courseRepository.courseChannelExists(channelId)) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
}

return courseRepository.findAccessibleChannel(channelId, viewerUserId);
```

Representative test snippet:

```java
mockMvc.perform(get("/api/v1/course-channels/{channelId}", UUID.randomUUID())
                .param("viewerUserId", instructorUserId.toString()))
        .andExpect(status().isNotFound());
```

## Verification

- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service test`
- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn verify`
- `npm run lint`
- `npm run build`
- PR #28 CI backend/frontend passed
- Greptile final summary updated to `5/5`

## Final Result

- Greptile final summary: `5/5`
- Greptile review threads resolved: 2
- Remaining Greptile comments: 0 unresolved
