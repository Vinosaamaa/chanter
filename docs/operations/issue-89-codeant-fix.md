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

## Pass 3 (`5e5e4f3`)

- **Finding:** duplicated pagination bounds in controller vs service.
- **Fix:** centralize `limit`/`offset` clamping in `CourseService.listCohortEnrollments`.
- **Finding:** sign-in blocked on cohort join; race when join fired without await.
- **Fix:** `CohortInviteRedirect` awaits join before navigation; `SignInPage` stores invite then delegates join to redirect.
- **Finding:** wizard double-submit.
- **Fix:** `isSubmitting` guard at top of `onSubmit`.

## Pass 4 (invite security + search + auth hardening)

- **Finding:** self-join IDOR — any user with cohort UUID could enroll.
- **Fix:** V7 migration adds `cohorts.invite_code`; join requires matching `invite` param; `GET /cohorts/{id}/invite` for instructors.
- **Finding:** client-side search capped at 500 rows.
- **Fix:** backend `search` query param + debounced server-side filter on enrollment page.
- **Finding:** unstable pagination order on duplicate `enrolled_at`.
- **Fix:** `ORDER BY enrolled_at DESC, learner_user_id ASC`.
- **Finding:** unbounded offset.
- **Fix:** `MAX_COHORT_ENROLLMENT_OFFSET = 10_000`.
- **Finding:** `CohortInviteRedirect` navigates on join failure.
- **Fix:** error/retry UI; navigate only after success.
- **Finding:** `cohort-invite.ts` re-queues permanent 4xx failures.
- **Fix:** re-store pending invite only on transient/network/5xx errors.
- **Finding:** invite URL missing `invite` token.
- **Fix:** enrollment page builds `/sign-in?cohort=…&invite=…` from API.
- **Finding:** cohort id path injection.
- **Fix:** `encodeURIComponent` on cohort paths in `onboarding-api.ts`.
- **Finding:** Course badge contrast on light accent colors.
- **Fix:** `ring-1 ring-black/20` on badge in `StudyServerHomePage`.
- **Finding:** search field placeholder-only label.
- **Fix:** visible “Search learners” label on enrollment page.
- **Finding:** extra DB round-trip for instructor auth pre-check.
- **Fix:** check `cohortHasInstructor` first; existence fallback only on forbidden path.

## Pass 5 (`4f8e85d`)

- **Finding:** PostgreSQL `NULL` search parameter typing broke enrollment list on real Postgres.
- **Fix:** split list query into search vs no-search SQL paths in `JdbcCourseRepository`.
- **Finding:** empty channel/context panels on `/app` picker with no `serverId`.
- **Fix:** render shell side panels only when `serverId` is present in `AppShellLayout`.

## Pass 6 (CodeAnt re-scan hardening)

- Added `cohort-invite.test.ts` covering invite param parsing, post-login storage, transient-only retry.
- Added `joinCohort` path-encoding test and backend smoke tests for invalid invite + server-side search.
- Hardened wizard submit with synchronous `useRef` guard; improved course badge text shadow for contrast.
- Prior pass 4 fixes remain in place for all 17 reported gate findings; this pass adds explicit test coverage.
