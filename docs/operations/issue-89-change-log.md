# Issue #89 — change log

**Slice:** Study Server home, create server, and enrollment polish  
**Branch:** `feature/89-study-server-enrollment-polish`  
**Mockups:** `create-study-server.png`, `study-server-home.png`, `cohort-enrollment.png`

## Summary

Polish onboarding flows toward mockup density: a three-step create Study Server wizard, richer server home course cards, and an instructor enrollment admin page backed by a new cohort enrollments list API.

## Changes

| Area | File | What |
|------|------|------|
| API | `CohortEnrollment.java`, `CohortEnrollmentResponse.java`, `CohortEnrollmentListResponse.java` | Domain + DTO for enrollment rows |
| Backend | `CourseRepository`, `JdbcCourseRepository`, `CourseService`, `CourseController` | `GET /api/v1/cohorts/{cohortId}/enrollments` with `limit`/`offset` (max 500) and `totalCount` |
| Test | `CourseEnrollmentSmokeTest.java` | Assert list after manual enroll |
| Types | `onboarding-types.ts` | `CohortEnrollmentRecord` |
| API client | `onboarding-api.ts`, `onboarding-api.test.ts` | `listCohortEnrollments()` |
| Hook | `use-cohort-enrollments.ts` | React Query hook for enrollment table |
| Wizard | `CreateStudyServerWizard.tsx`, `CreateStudyServerPage.tsx` | Basics → invite notes → review with icon preview sidebar |
| Home | `StudyServerHomePage.tsx` | Course cards with description from session storage, CTA density |
| Enrollment | `CohortEnrollmentPage.tsx` | Learner table, search, pagination, invite link copy, manual enroll |

## Acceptance mapping

- [x] Create-server flow: multi-step wizard with description, icon preview, invite step, review sidebar.
- [x] Server home course cards and CTAs (server picker/delete remains **#93**).
- [x] Enrollment: learner table, invite link, search/pagination; TA column shows unassigned (backend slice deferred, same as **#56**).
- [x] Empty states and validation copy aligned with product tone.
- [ ] Owner browser sign-off vs mockups (pending review).

## Browser demo

1. Sign in as owner: `dev-demo-owner@chanter.local` / `chanter-dev-demo`.
2. **Create Study Server** — walk through Basics (name + description) → Invite (notes) → Review; confirm icon preview and summary sidebar.
3. **Server home** — create a course; confirm card shows title, cohort, channel count, and stored description.
4. Open **Enrollment** for the cohort — copy invite link (`/sign-in?cohort=…`), manually enroll learner id from demo learner account.
5. Sign in as learner (`dev-demo-learner@chanter.local`) via invite link or existing session; confirm enrollment appears in owner table after refresh.

## Deferrals

- **Description persistence:** stored in `sessionStorage` (`chanter:study-server-description:{serverId}`); no backend column yet.
- **Icon upload:** preview via `StudyServerIcon` only; no file upload.
- **TA assignment:** UI placeholder; requires future backend slice.
- **Learner display names:** table shows truncated user id (no user lookup API).

## PR review (CodeAnt AI)

Log review passes in `docs/operations/issue-89-codeant-fix.md`. Trigger with `@codeant-ai: review` only (no extra text).

## Verification

```bash
mvn -B -pl community-service test -Dtest=CourseEnrollmentSmokeTest
(cd frontend && npm run test -- --run onboarding)
(cd frontend && npm run lint && npm run build)
```
