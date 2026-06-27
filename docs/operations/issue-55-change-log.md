# Issue #55 — Production Instructor Dashboard And SaaS Plan UI

## Summary

Replaces the `/app/instructor-dashboard` stub with a production page that loads live aggregates from the analytics-service instructor dashboard API and lets Study Server owners update the SaaS plan tier.

## Route

- `/app/instructor-dashboard?serverId={uuid}` — optional `serverId` query selects the Study Server (defaults to the first accessible server).

Top bar **Instructor Dashboard** link opens this page with active-state styling.

## Frontend

New feature module: `frontend/src/features/instructor-dashboard/`

- `instructor-dashboard-api.ts` — dashboard, SaaS plan GET/PATCH, study server details (owner check)
- `hooks/use-instructor-dashboard-page.ts` — load/refresh metrics, owner-only plan updates
- `components/InstructorDashboardPage.tsx` — metric cards, Office Hours summary, SaaS usage panel

## Widgets (live backend data)

| Widget | Source field |
|--------|----------------|
| Unanswered support questions | `unansweredSupportQuestions` |
| Repeated question groups | `repeatedQuestionGroups` |
| TA queue load | `openTaQueueItems` |
| Low-confidence handoffs | `lowConfidenceHandoffs` |
| Office Hours | `liveOfficeHoursSessions`, `scheduledOfficeHoursSessions`, `officeHoursWaitlistEntries` |
| Approved FAQs | `approvedFaqCount` |
| AI usage | `aiInvocationCount` / `aiInvocationLimit` / `remainingAiInvocations` |

## Access

- Dashboard: `GET /api/v1/study-servers/{id}/instructor-dashboard?viewerUserId=` (instructor or owner).
- Plan read: `GET /api/v1/study-servers/{id}/saas-plan`.
- Plan update: `PATCH /api/v1/study-servers/{id}/saas-plan` (JWT owner only).

Unauthorized users see a dedicated access-denied message (403).

## Browser test (2026-06-27)

**Setup:** `/dev/demo` → create Study Server + course → **Open app shell as Owner** → top bar **Instructor Dashboard**.

| Step | Result |
|------|--------|
| Page shell & server selector | ✅ Renders; `?serverId=` set from first accessible server |
| Load metrics | ✅ Metric cards, Office Hours, SaaS usage panel (after backend `X-User-Id` fixes) |
| Plan upgrade | ✅ Owner sees plan tier dropdown and **Update plan** |

**Backend fix (same PR):** `analytics-service` `HttpCommunityServiceClient` and `agent-service` `HttpStudyServerSaasPlanClient` now send `X-User-Id` on community calls (required since JWT retrofit #30). Without this, dashboard returned 502 with Spring JSON error bodies.

**Local stack note:** Instructor dashboard aggregates via `analytics-service` → community, message, and agent services. If the gateway was started before analytics, restart the gateway (`make backend-gateway`) then click **Refresh**.

**#55 testing note:** Implemented without TDD (see HANDOFF / `agent-workflow.md`). **#56+** uses vertical-slice TDD.

## TDD

Issue #55 was implemented test-last (browser/manual verification only). **From issue #56**, follow `docs/operations/agent-workflow.md` § Test-driven development.
