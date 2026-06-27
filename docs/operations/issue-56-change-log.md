# Issue #56 — Production Onboarding And Enrollment Flows

## Summary

Production routes for Study Server creation, server home (course cards), and cohort enrollment — replacing the dev-demo-only onboarding path.

## Routes

| Route | Purpose |
|-------|---------|
| `/app/onboarding/create-study-server` | Owner wizard — creates server, lands on home |
| `/app/servers/:serverId/home` | Study Server home with course cards |
| `/app/servers/:serverId/courses/:courseId/enrollment` | Instructor enrolls learner + channel access preview |

`/app` with no servers redirects to the create wizard. `/app/servers/:serverId` redirects to home.

## Module

`frontend/src/features/onboarding/`

- `onboarding-api.ts` — create server, create course, enroll learner
- `hooks/use-cohort-enrollment.ts` — enrollment form state
- `components/CreateStudyServerPage.tsx`
- `components/StudyServerHomePage.tsx`
- `components/CohortEnrollmentPage.tsx`

## TDD (#56 policy)

Added **Vitest** + **Testing Library** (`npm test`):

- `onboarding-api.test.ts` — API client contracts
- `hooks/use-cohort-enrollment.test.ts` — empty id guard + successful enroll

## Browser test (manual)

1. Register/sign in as owner → redirected to create Study Server if none exist
2. Create server → lands on server home
3. Create course + cohort on home
4. **Manage enrollment** → paste learner user id → enroll
5. Sign in as learner → server home / sidebar shows course under **My courses**

**Auth note (2026-06-27):** `auth-store` now persists `refreshToken` so sessions survive reload. Stale sessions without a refresh token show a friendly message and redirect to sign-in instead of raw `Request failed: 401 Unauthorized`.

## Deferred

- TA assignment UI (no backend API in MVP)
- Learner lookup by email (enrollment uses user id paste for now)
