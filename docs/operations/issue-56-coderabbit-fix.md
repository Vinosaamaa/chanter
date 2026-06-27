# Issue #56 — CodeRabbit fix log

## Pass 1 (PR #73)

Addressed seven inline comments from the first CodeRabbit review. One security item deferred (see table).

| Comment | Resolution |
|---------|------------|
| **Major** — `package.json`: Vitest 4 / Vite 8 Node engine alignment | Added `engines.node` (`^20.19.0 \|\| >=22.12.0`); CI frontend job uses Node 22 |
| **Major** — `CohortEnrollmentPage`: hard-wired `cohorts[0]` | Cohort `<select>` when a course has multiple cohorts; selected id drives enroll + preview |
| **Major** — `CohortEnrollmentPage`: query errors shown as not-found | `navigationQuery.isError` branch before missing-course state |
| **Major** — `use-cohort-enrollment`: empty `cohortId` posts to invalid path | Guard `!cohortId` before API call; split `isSubmitting` guard; test added |
| **Trivial** — `StudyServerHomePage`: inconsistent 401 / error formatting | Reuse `isUnauthorizedApiError`, `clearSession`, `formatUserFacingApiError` like create-server page |
| **Minor** — `ChannelSidebarColumn`: subtitle on all routes | Route-specific subtitle from `channelScope` |
| **Minor** — `ServerSwitcherColumn`: active server accessibility | `aria-current="page"` and distinct `aria-label` for active server |
| **Major** — `auth-store`: refresh token in client-readable storage | **Deferred:** MVP keeps Zustand persist so sessions survive reload (#56 auth fix). Migrate refresh to HttpOnly cookie / server session in auth hardening (#30 follow-up). |

**Verification:** `cd frontend && npm test && npm run lint && npm run build`

**Remaining threads:** HttpOnly refresh-token storage (deferred).

## Pass 2

| Comment | Resolution |
|---------|------------|
| **Minor** — `CohortEnrollmentPage`: stale enrollment feedback on cohort switch | Call `enrollment.reset()` when the cohort `<select>` changes |
| **Minor** — `use-cohort-enrollment`: success message persists on validation failure | Clear `successMessage` in missing-cohort and empty-learner branches |

**Verification:** `cd frontend && npm test && npm run lint && npm run build`
