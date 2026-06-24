# Issue #22 Change Log: Run Office Hours For A Cohort

Date: 2026-06-21  
Branch: `feature/22-run-office-hours-for-cohort`  
Issue: `#22 Slice: Run Office Hours For A Cohort`

## Acceptance Criteria Covered

- Instructor can schedule Office Hours for a Cohort with start/end window.
- Enrolled learner can join the waitlist only during the open window.
- Instructor (TA proxy for MVP) can admit the next learner and join voice for the live session.
- Smoke tests cover schedule boundaries, enrollment checks, and forbidden scheduling.

## 1. Community Service — Office Hours

- Flyway `V4__create_office_hours_tables.sql` (`office_hours_sessions`, `office_hours_waitlist_entries`)
- `GET /api/v1/cohorts/{cohortId}/office-hours-access?userId=`
- `POST /api/v1/cohorts/{cohortId}/office-hours` — schedule (instructor)
- `GET /api/v1/cohorts/{cohortId}/office-hours?viewerUserId=` — list sessions
- `GET /api/v1/office-hours/{sessionId}?viewerUserId=`
- `POST /api/v1/office-hours/{sessionId}/waitlist` — learner joins during window
- `GET /api/v1/office-hours/{sessionId}/waitlist?viewerUserId=`
- `POST /api/v1/office-hours/{sessionId}/admit-next` — instructor admits next waiting learner + voice presence
- `POST /api/v1/office-hours/{sessionId}/voice-join` — instructor joins voice
- `POST /api/v1/office-hours/{sessionId}/learner-voice-join` — admitted learner joins voice
- `POST /api/v1/office-hours/{sessionId}/end` — instructor ends session
- Reuses Study Server `study-room` Voice Channel transport (#14)

## 2. Gateway And Frontend Demo

- Gateway community route includes `/api/v1/office-hours/**` and `office-hours-access`
- Demo panel: **Cohort Office Hours (#22)** with schedule, learner waitlist, admit + voice

## Verification

```bash
mvn -pl community-service verify
npm run lint && npm run build
```

## Deferred

- Dedicated TA role assignment deferred; instructors manage Office Hours for MVP.
- `TODO(#auth)` caller `userId` query/body params until #30.
