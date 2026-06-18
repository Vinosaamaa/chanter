# Issue 16 Change Log: Post A Support Question In A Course Channel

Date: 2026-06-17  
Branch: `feature/16-post-support-question-in-course-channel`  
Issue: `#16 Slice: Post A Support Question In A Course Channel`

## Acceptance Criteria Covered

- Enrolled learner can post in the permitted Course Channel (`#questions`).
- Message is durable and idempotent via `idempotencyKey`.
- Support Question workflow record is created with `UNANSWERED` status.
- Instructor can list unanswered Support Questions for the channel.
- Tests cover unauthorized posting and unknown channel responses.

## 1. Community Service — Support Question Access

- Added `GET /api/v1/course-channels/{channelId}/support-question-access?userId=`.
- Enrolled learners receive `canPostSupportQuestion=true`.
- Course Instructors receive `canViewUnansweredSupportQuestions=true`.
- Extended `CourseEnrollmentSmokeTest` for the access matrix.

## 2. Message Service — Durable Support Questions

- Flyway `V3__create_support_question_tables.sql` adds `channel_messages` and `support_questions`.
- `POST /api/v1/course-channels/{channelId}/support-questions` creates durable messages + workflow rows.
- `GET /api/v1/course-channels/{channelId}/support-questions?viewerUserId=` lists `UNANSWERED` items for instructors.
- `HttpCourseChannelAccessClient` calls community-service for authorization; `TestCourseChannelAccessClient` backs smoke tests.
- Added `SupportQuestionSmokeTest` (4 cases).

## 3. Gateway And Frontend Demo

- Gateway routes `/api/v1/course-channels/*/support-questions` to message-service (order `-1`).
- Frontend demo panel: post as Learner, list unanswered as Instructor after enrollment.

## Verification

- `mvn -pl community-service,message-service verify`
- `npm run lint && npm run build`

## Deferred

- Real caller identity remains `TODO(#auth)` / issue #30.
- TA role not modeled yet; instructor-only unanswered list for this slice.
- `QuestionDetected` outbox event deferred to realtime/analytics slices.
