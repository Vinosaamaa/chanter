# Issue #50 Change Log

Issue: [#50 Study Server App Shell And Navigation](https://github.com/Vinosaamaa/chanter/issues/50)

## Summary

Delivered the four-column production app shell (server switcher, enrollment-scoped channel sidebar, conversation placeholder, context placeholder) with route-driven channel selection. Added community-service navigation APIs so owners/instructors see the full catalog while enrolled learners see only their courses.

## Backend

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/study-servers` | List study servers accessible to the authenticated user |
| `GET /api/v1/study-servers/{id}/navigation` | Enrollment-scoped sidebar tree (server channels + My courses) |

| Area | Change |
|------|--------|
| `StudyServerNavigationService` | Filters grant candidates by viewer scope and study-server membership |
| `JdbcCourseRepository` | `listAccessibleStudyServers` query (owner, roles, instructor, enrollment) |
| `StudyServerNavigationSmokeTest` | Owner vs learner sidebar assertions |

## Frontend

| Path | Purpose |
|------|---------|
| `features/shell/layouts/AppShellLayout.tsx` | Four-column shell + top bar |
| `features/shell/components/*` | Server switcher, channel sidebar, conversation/context placeholders |
| `features/shell/shell-api.ts` | Navigation API client |
| `features/shell/shell-routes.ts` | Channel paths + default redirect helper |
| `app/router.tsx` | `/app/servers/:serverId/study-channels/:channelId` and `course-channels` routes |

Top bar stubs: **Friends** (`#31`), **Instructor Dashboard** (`#55`).

## Verification

```bash
cd backend && mvn -pl community-service -am test -Dtest=StudyServerNavigationSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
cd frontend && npm run lint && npm run build
```

Manual browser demo:

1. Sign in (or use `/dev/demo` personas to seed a study server + course + enrollment).
2. Open `/app` — redirects to first accessible server/channel.
3. As **owner**: sidebar shows Study Server channels + all courses.
4. As **learner**: sidebar shows only enrolled course channels (no second course).

## Follow-ups

- #51 — realtime conversation column
- #52 / #53 — context column panels
- #55 — instructor dashboard route
- #56 — onboarding when user has zero study servers
