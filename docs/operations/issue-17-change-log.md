# Issue 17 Change Log: Upload An Approved Course Resource

Date: 2026-06-18  
Branch: `feature/17-upload-approved-course-resource`  
Issue: `#17 Slice: Upload An Approved Course Resource`

## Acceptance Criteria Covered

- Instructor can upload/register a Course Resource on a Course.
- Resource metadata records Course scope and `aiApproved` status.
- Enrolled learner can list and download permitted resources.
- Unauthorized users are blocked from list and download paths.

## 1. Community Service — Course Resource Access

- Added `GET /api/v1/courses/{courseId}/resource-access?userId=`.
- Course Instructors receive `canUploadCourseResource=true` and `canViewCourseResources=true`.
- Enrolled learners receive `canViewCourseResources=true` only.
- Extended `CourseEnrollmentSmokeTest` for the access matrix.

## 2. Media Service — Course Resource Storage

- Bootstrapped `media-service` on port `8084` with Flyway `V1__create_course_resource_tables.sql`.
- `POST /api/v1/courses/{courseId}/course-resources` accepts multipart upload and stores bytes on local disk.
- `GET /api/v1/courses/{courseId}/course-resources?viewerUserId=` lists course-scoped metadata.
- `GET /api/v1/course-resources/{resourceId}/content?viewerUserId=` downloads resource bytes.
- `HttpCourseResourceAccessClient` calls community-service; `TestCourseResourceAccessClient` backs smoke tests.
- Added `CourseResourceSmokeTest` (4 cases).

## 3. Gateway, Infra, And Frontend Demo

- Gateway routes `/api/v1/courses/*/course-resources` and `/api/v1/course-resources/**` to media-service.
- PostgreSQL init adds `chanter_media` database.
- Frontend demo panel: upload as Instructor (file picker), list as Learner after enrollment.

## Verification

- `mvn -pl community-service,media-service verify`
- `npm run lint && npm run build`

## Deferred

- Real caller identity remains `TODO(#auth)` / issue #30.
- MinIO/S3 object storage integration deferred; local filesystem used for MVP slice.
- Search indexing and agent grants deferred to later slices (#18+).
