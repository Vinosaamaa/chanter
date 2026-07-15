# Issue #142 - change log

## Summary

Replace fabricated Home and Course Overview cards with permission-scoped aggregate data from local community-service records. Progress stays honestly empty (`null` / `NO_CURRICULUM`) until a curriculum model exists.

## Backend

- `GET /api/v1/me/home-summary` — `HomeSummaryController` + `HomeSummaryService`
- `GET /api/v1/courses/{courseId}/overview-summary?cohortId=` — `CourseOverviewSummaryController` + `CourseOverviewSummaryService`
- Aggregates from local community data only:
  - Accessible study servers / navigation courses
  - Upcoming / LIVE office hours (cohort-scoped)
  - Upcoming community events
  - Published announcements (count/list for attention; not unread)
  - Voice presence for study-room up-next when a course voice channel exists
  - Instructor display names via `AuthUserDirectoryClient`
- Always returns `progress: null`, `progressUnavailableReason: "NO_CURRICULUM"`, `partialFailures: []`
- Deep-link `href` strings match frontend routes
- Smoke tests: `HomeSummarySmokeTest`, `CourseOverviewSummarySmokeTest`

## Gateway

- Added `/api/v1/me/**` and `/api/v1/courses/*/overview-summary` to community-service route predicates

## Frontend

- `features/home/home-summary-api.ts` (+ types + test)
- `features/course-overview/course-overview-summary-api.ts` (+ types + test)
- `build-home-view-model.ts` maps API response + greeting/date only (no fabricated progress/attention/upNext)
- `HomePage` / `CourseOverviewPage` fetch via react-query with loading/error/empty states
- Progress bar hidden when `progress` is null; Up next Join uses `Link` when `href` present

## Deferred

- Question-answered / resource / message-backed recent activity and attention items — requires optional message/media RestClients (follow-ups)
- Unread counts — #143
- Full calendar surface — #144
- Curriculum-backed progress % — no model yet
