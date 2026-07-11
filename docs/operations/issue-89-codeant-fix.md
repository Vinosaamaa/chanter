# Issue #89 — CodeAnt fix log

**PR:** #113  
**Branch:** `feature/89-study-server-enrollment-polish`

## Pass 1

- Triggered `@codeant-ai: review` on PR open.
- CI + quality gates passed.
- **Finding:** unbounded enrollment list query (`JdbcCourseRepository.java`).
- **Fix:** add `limit`/`offset` query params (default 50, max 500), `totalCount` in response, and server-side pagination on enrollment page.

## Pass 2

- **Finding:** invite URL `?cohort=` not wired to enrollment after sign-in.
- **Fix:** `POST /api/v1/cohorts/{cohortId}/join` self-enroll + `SignInPage` / `CohortInviteRedirect` pending invite handling.
- **Finding:** stale copy message when switching cohorts.
- **Fix:** reset `copyMessage` on cohort select change.
- **Finding:** `sessionStorage.setItem` failure blocks successful server creation.
- **Fix:** wrap description storage in isolated try/catch.
- **Finding:** enrollment list test missing `enrolledByUserId`.
- **Fix:** update mocked API contract in `onboarding-api.test.ts`.
