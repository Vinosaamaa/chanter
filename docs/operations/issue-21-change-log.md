# Issue 21 Change Log: Route Low-Confidence Answer To TA Queue

Date: 2026-06-23  
Branch: `feature/21-route-low-confidence-to-ta-queue`  
Issue: `#21 Slice: Route Low-Confidence Answer To TA Queue`

## Acceptance Criteria Covered

- Learner can add a low-confidence Support Question to the Cohort TA Queue from the handoff UI.
- Cohort TA (MVP: Course Instructor) can list, pick up, resolve, and cancel open queue items.
- Queue items are persisted with cohort scoping and auditable timestamps.
- Tests cover the happy path and unauthorized list access.

## 1. Community Service — Cohort TA Queue Access

- Added `GET /api/v1/cohorts/{cohortId}/ta-queue-access?userId=` returning `canAddToTaQueue` (enrolled learner) and `canManageTaQueue` (course instructor acting as TA for MVP).
- `JdbcCourseRepository.findCohortTaQueueAccess` — enrolled learner can add, instructor can manage.

## 2. Message Service — TA Queue

- Flyway `V5__create_ta_queue_tables.sql` for `ta_queue_items`.
- `CohortTaQueueAccessClient` (HTTP + test double) calling community-service.
- `TaQueueService.addFromSupportQuestion` requires Support Question status `AI_LOW_CONFIDENCE` and checks both cohort and course-channel access.
- APIs:
  - `POST /api/v1/cohorts/{cohortId}/ta-queue`
  - `GET /api/v1/cohorts/{cohortId}/ta-queue`
  - `PATCH .../{itemId}/pickup`
  - `PATCH .../{itemId}/resolve`
  - `PATCH .../{itemId}/cancel`
- `TaQueueSmokeTest` (2 cases: happy path + unauthorized list).

## 3. Gateway And Frontend Demo

- Gateway route `message-service-ta-queue` for `/api/v1/cohorts/*/ta-queue/**` to message-service (`order: -1`).
- Frontend demo section **TA Queue (#21)**: add from low-confidence handoff, instructor list/pickup/resolve.

## Verification

- `mvn -pl community-service,message-service verify`
- `npm run lint && npm run build`

## Deferred

- Dedicated Cohort TA role assignment (instructor proxies as TA in MVP).
- Real caller identity remains `TODO(#auth)` / issue #30.
- Office Hours handoff path deferred to #22.
