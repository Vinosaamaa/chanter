# Issue #54 — Production Support Operations UI

## Summary

Adds production shell routes and panels for cohort-scoped support operations:

- **TA queue** — instructors/TAs list open items, pick up, and resolve handoffs.
- **Office Hours** — instructors schedule sessions, admit learners from the waitlist; learners join the waitlist.
- **FAQ approval** — instructors review repeated-question candidates, approve new FAQs, and edit existing ones.

## Routes

Under the Study Server shell:

- `/app/servers/:serverId/courses/:courseId/support/ta-queue`
- `/app/servers/:serverId/courses/:courseId/support/office-hours`
- `/app/servers/:serverId/courses/:courseId/support/faq-approval`

Sidebar **Support** links appear under each course.

## Frontend

New feature module: `frontend/src/features/support-operations/`

- API clients: `access-api.ts`, `ta-queue-api.ts`, `office-hours-api.ts`, `faq-api.ts`
- Hooks: `use-ta-queue-panel.ts`, `use-office-hours-panel.ts`, `use-faq-approval-panel.ts`
- Panels: `TaQueuePanel`, `OfficeHoursPanel`, `FaqApprovalPanel`, `SupportOperationPage`

Shell helpers in `shell-routes.ts`: `supportOperationPath`, `findCourseById`, `findQuestionsChannel`.

## Access

- TA queue / Office Hours access: JWT-backed community endpoints (`ta-queue-access`, `office-hours-access`).
- TA queue mutations and FAQ flows still pass `viewerUserId` / `actorUserId` per existing message-service contracts (#30 partial retrofit).

## Browser test (2026-06-26)

Verified on local stack (`/dev/demo` seed + production shell as Demo Owner):

- **TA queue:** open item → Pick up → Resolve (empty queue + success message)
- **FAQ approval:** 1 candidate group (6 linked questions) → approve → listed under Approved FAQs with Edit
- **Office Hours:** Schedule now → session created

### Fix during test

`message-service` `HttpCourseResourceAccessClient` still called `resource-access?userId=` after #30 JWT retrofit, causing **502** on `GET /approved-faqs`. Fixed to send `AuthHeaders.USER_ID` like media-service. FAQ hook now loads candidates and approved FAQs independently.

## PR

`feature/54-production-support-operations-ui` — Closes #54
