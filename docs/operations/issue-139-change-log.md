# Issue #139 - change log

**Branch:** `feature/139-truthful-study-server-course-lifecycle`
**Issue:** Truthful Study Server and Course lifecycle

## Goal

Persist every owner-submitted Study Server and Course field (or remove it from submit), expose durable server invitations and course governance APIs with backend authorization, and wire the approved v2 owner flows without fixture-only behavior.

## What changed

### Study Server lifecycle (backend)

- Migration `V15__study_server_and_course_lifecycle.sql` adds `study_servers.description`, `study_servers.server_type`, `courses.description`, `courses.archived_at`, `study_server_invitations`, and changes new courses to default `published = FALSE`.
- `POST /api/v1/study-servers` accepts `description`, `serverType` (`SCHOOL` | `PROGRAM` | `PERSONAL`), and `inviteEmails`; invitations are stored in `study_server_invitations`.
- `GET /api/v1/study-servers/{id}` and create responses include `description`, `serverType`, and `pendingInvitations`.
- `GET /api/v1/study-server-invitations` lists pending invitations for the signed-in user.
- `POST /api/v1/study-servers/{id}/invitations/{invitationId}/accept` grants `STUDY_SERVER_MEMBER`.
- Accessible Study Servers include members who accepted invitations.

### Course lifecycle (backend)

- `POST /study-servers/{id}/courses` accepts optional `description` and optional `cohortName`; omitting cohort creates a draft (`published=false`).
- Coupled create (title + cohort) keeps legacy published behavior for existing flows.
- New owner-only endpoints: `POST /courses/{id}/cohorts`, `PATCH /courses/{id}/instructor`, `PATCH /courses/{id}` (metadata), `GET /courses/{id}`, `POST publish|unpublish|archive`.
- Instructor assignment accepts `instructorUserId` or `instructorEmail`; grants course-scoped instructor role only.
- Catalog continues to exclude unpublished and archived courses.
- Authorization tests in `CourseLifecycleSmokeTest` and `StudyServerLifecycleSmokeTest`.

### Gateway routing

- Extended `community-service` route in `gateway-service` `application.yml` for course lifecycle (`GET/PATCH /courses/*`, cohorts, instructor, publish/unpublish/archive) and `GET /study-server-invitations`.

### Frontend (v2 owner flows)

- `CreateStudyServerV2Page` posts description, server type, and invite emails; removed `sessionStorage` description hack.
- `CommunityDiscoverPage` create modal supports description, draft vs publish-ready modes; draft navigates to course settings.
- New `CourseGovernancePage` at `/app/servers/:serverId/courses/:courseId/settings` for metadata edit, add cohort, assign instructor by email, publish/unpublish, and archive with confirmation.
- `course-lifecycle-api` module + tests; extended `onboarding-api` types and tests.
- `HomeStudyServerInvites` on Home shows pending server invitations with accept action.

## Tests

- Backend: `CourseLifecycleSmokeTest`, `StudyServerLifecycleSmokeTest` (+ metadata patch, invitation list).
- Frontend: `course-lifecycle-api.test.ts`, updated `onboarding-api.test.ts`, `HomePage.test.tsx` mock for invites panel.
- Java 21: `mvn -pl community-service test` (67 tests).
- Frontend: `npm run lint`, `npm run test`, `npm run build`.

## Verification

- Migration V15 rehearsed on local PostgreSQL product stack.
- Owner journey verified at mobile, 720p, 1080p, and 4K-representative widths through Gateway.
- Persistence after refresh, truthful controls, no horizontal overflow, no console errors, no failed API calls.
