# Issue #140 - change log

## Summary

Replace mock Community Events UI with Study Server–scoped durable events and RSVPs.

## Backend

- Migration `V16__community_events_and_rsvp.sql` — `community_events` + `community_event_rsvps`
- APIs under `/api/v1/study-servers/{id}/events` — list/filter, get, create, update, cancel, RSVP upsert, ICS export
- Owner-only create/edit/cancel; members can list/RSVP within visibility (`HUB` / `COURSE` / `COHORT`)
- Idempotent RSVP upsert; capacity conflict when full

## Frontend

- `frontend/src/features/community-events/` API + types + tests
- `CommunityEventsPage.tsx` loads real events, filters, RSVP, share link, add-to-calendar (ICS + Calendar route), create/edit/cancel for owners
- Deep link `?event={id}` supported

## Verification

- `CommunityEventSmokeTest`
- Frontend `community-events-api.test.ts`, lint, tsc
