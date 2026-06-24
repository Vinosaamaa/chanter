# Issue #21 Change Log: Route Low-Confidence Answer To TA Queue

Date: 2026-06-24  
Branch: `feature/21-route-low-confidence-to-ta-queue`  
Issue: `#21 Slice: Route Low-Confidence Answer To TA Queue`

## Acceptance Criteria Covered

- Learner can add a low-confidence Support Question to the Cohort TA Queue from the demo handoff UI.
- Cohort instructor (TA proxy for MVP) can list, pick up, and resolve queue items.
- Queue rows are persisted with auditable status transitions (`OPEN` → `PICKED_UP` → `RESOLVED` / `CANCELLED`).
- Smoke tests cover happy path and forbidden list access.

## 1. Community Service — Cohort TA Queue Access

- `GET /api/v1/cohorts/{cohortId}/ta-queue-access?userId=`
- Enrolled learners: `canAddToTaQueue=true`
- Course instructors: `canManageTaQueue=true`

## 2. Message Service — TA Queue

- Flyway `V6__create_ta_queue_tables.sql` (+ Postgres partial unique index in `migration-postgresql/V6_1`)
- `POST /api/v1/cohorts/{cohortId}/ta-queue`
- `GET /api/v1/cohorts/{cohortId}/ta-queue?viewerUserId=`
- `PATCH .../pickup`, `PATCH .../resolve`, `PATCH .../cancel`
- `HttpCohortTaQueueAccessClient` + `TestCohortTaQueueAccessClient`
- `TaQueueSmokeTest` (2 cases)

## 3. Gateway And Frontend Demo

- Gateway routes `/api/v1/cohorts/*/ta-queue/**` to message-service (`order: -1`)
- Demo: **Add to TA Queue (#21)** after low-confidence assistant answer; instructor queue panel with pickup/resolve

## Verification

```bash
mvn -pl community-service,message-service verify
npm run lint && npm run build
```

## Deferred

- Dedicated TA role assignment deferred; instructors manage queue for MVP.
- `TODO(#auth)` caller `userId` query params until #30.
