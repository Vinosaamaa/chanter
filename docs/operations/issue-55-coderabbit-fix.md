# Issue #55 — CodeRabbit fix log

## Pass 1

Resolved all inline comments from the first CodeRabbit review on PR #72.

| Comment | Resolution |
|---------|------------|
| **Minor** — `HttpCommunityServiceClient`: stale `userId` query on `fetchCommunityMetrics` | Dropped query param; community `InstructorDashboardController` now uses `@RequestAttribute(USER_ID)` like grant-candidates |
| **Minor** — `use-instructor-dashboard-page.ts`: stale `isOwner` when switching servers | Reset `setIsOwner(false)` at start of load effect |
| **Minor** — `HANDOFF.md`: stale “browser/gateway restart pending” | Updated #55 row to PR #72 + browser verified |
| **Minor** — `issue-55-coderabbit-fix.md`: placeholder status | Replaced with this pass log |
| **Minor** — `HttpStudyServerSaasPlanClient`: authorize GET `/saas-plan` by membership | **Deferred:** community `GET /saas-plan` is intentionally readable for any authenticated caller; `PATCH` is owner-only. Agent callers pass `actingUserId` only after grant-candidate / AI-invocation auth paths already validated the user |
