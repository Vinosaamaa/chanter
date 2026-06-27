# Issue #55 — CodeRabbit fix log

## Pass 1

Addressed five inline comments from the first CodeRabbit review on PR #72. One item deferred (see table).

| Comment | Resolution |
|---------|------------|
| **Minor** — `HttpCommunityServiceClient`: stale `userId` query on `fetchCommunityMetrics` | Dropped query param; community `InstructorDashboardController` now uses `@RequestAttribute(USER_ID)` like grant-candidates |
| **Minor** — `use-instructor-dashboard-page.ts`: stale `isOwner` when switching servers | Reset `setIsOwner(false)` at start of load effect |
| **Minor** — `HANDOFF.md`: stale “browser/gateway restart pending” | Updated #55 row to PR #72 + browser verified |
| **Minor** — `issue-55-coderabbit-fix.md`: placeholder status | Replaced with this pass log |
| **Minor** — `HttpStudyServerSaasPlanClient`: authorize GET `/saas-plan` by membership | **Deferred:** community `GET /saas-plan` is readable for any authenticated caller; `PATCH` is owner-only. Agent passes `actingUserId` only after grant-candidate or AI-invocation paths already validated the user |

**Verification:** `npm run build` (frontend); browser `/dev/demo` → Owner → Instructor Dashboard metrics load.

**Remaining threads:** saas-plan GET membership authz (deferred; pre-existing community contract).

## Pass 2

| Comment | Resolution |
|---------|------------|
| **Minor** — fix log overstated “resolved all” while deferral remains | Rewrote Pass 1 summary and added verification / remaining-threads sections |
| **Minor** — `HANDOFF.md` stale startup prompt (#48) and TDD note (#12) | Startup prompt now points to #55/#56; notes align with #47–#55 test-last / #56+ TDD policy |

**Verification:** `npm run build` (frontend).

**Remaining threads:** saas-plan GET membership authz (deferred).
