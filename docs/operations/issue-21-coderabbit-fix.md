# Issue #21 — CodeRabbit fix log

Date: 2026-06-24  
PR: #41

## Findings addressed

| Severity | File | Action |
|----------|------|--------|
| Major | `V6__create_ta_queue_tables.sql` | `CHECK` constraint on `status` enum values |
| Major | `TaQueueSmokeTest.java` | Added stranger cannot pickup/resolve test |
| Major | `App.tsx` | Reset TA queue state when study server/course context changes |
| Minor | `TaQueueService.java` | Resolve requires `PICKED_UP`; removed duplicate access fetch |
| Minor | `HttpCohortTaQueueAccessClient.java` | Preserve downstream exception as cause |
| Trivial | `TestCohortTaQueueAccessClient.java` | Removed dead `registerCohort` access rule |
| Minor | `App.tsx` | Show Resolve only for `PICKED_UP` items |

## Deferred

| Severity | File | Reason |
|----------|------|--------|
| Critical/Major | `TaQueueController`, `TaQueueActorRequest`, `CourseController` | Caller `userId` / `actorUserId` from query/body is `TODO(#auth)` until #30 — same pattern as #16–#20 |
| Major | `HttpCohortTaQueueAccessClient.java` | RestClient connect/read timeouts deferred; matches other community HTTP clients in MVP |
| Minor | `TaQueueService.java` | Race on duplicate queue insert mitigated by app check + Postgres partial unique index (`V6_1`) |

## Verification

```bash
mvn -pl community-service,message-service verify
npm run lint && npm run build
```
