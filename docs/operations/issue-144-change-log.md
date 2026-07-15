# Issue #144 - change log

## Summary

Turn Calendar into a membership-scoped cross-course aggregate of Office Hours and Community events (with RSVP), driven by real date range / type / search queries. Join and Going use real OH deep-links and the #140 RSVP API.

## Backend (community-service)

- `GET /api/v1/me/calendar?from=&to=&types=&search=` — `CalendarController` + `CalendarService`
- Aggregates accessible Office Hours sessions and Community events overlapping `[from, to)`
- `types`: `OFFICE_HOURS`, `EVENT`, `DEADLINE`, `GOING` (RSVP filter)
- `search`: case-insensitive match on title / context / event description / location
- Deep-link `href`s:
  - OH → `/app/servers/{server}/courses/{course}/office-hours?cohort=&session=`
  - Events → `/app/servers/{server}/community/events?event=`
- **Deadlines omitted:** learning resources have no due-date field; response `notes` documents honesty (createdAt alone is not treated as a calendar date)
- Smoke test: `CalendarSmokeTest`

## Gateway

- `/api/v1/me/calendar` is covered by existing community `Path=/api/v1/me/**`
- Does not collide with notification-service `/api/v1/me/notifications/**` (order `-2`)
- Comment added in `gateway-service` `application.yml`

## Frontend

- `features/calendar/` API + types + unit test
- `CalendarPage.tsx` rewritten: month nav, Today, day select, type filters, `?q=` search, loading/empty/error
- Join → OH `href` link; Going → `upsertCommunityEventRsvp` from community-events (#140)
- Selected-day agenda + upcoming-week rows deep-link to source records
- Calendar top-bar search is editable and syncs `?q=` (does not open global resource search)
- Tests: `CalendarPage.test.tsx`, `calendar-api.test.ts`, V2TopBar calendar search case

## Deferred

- Assignment / resource deadlines once a due-date model exists
- Study-room presence rows on Calendar (Home Up next already surfaces live voice)
