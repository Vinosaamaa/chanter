# Issue #23 Change Log: Show Instructor Dashboard

Date: 2026-06-21  
Branch: `feature/23-show-instructor-dashboard`  
Issue: `#23 Slice: Show Instructor Dashboard`

## Acceptance Criteria Covered

- Instructor views dashboard aggregates for a permitted Study Server.
- Aggregates combine community Office Hours load, message-service support/queue/FAQ metrics, and agent AI usage.
- Unauthorized users are denied via existing grant-candidate access checks.
- Controller test covers aggregate response shape.

## 1. Analytics Service (bootstrap)

- New `analytics-service` module (port 8086)
- `GET /api/v1/study-servers/{studyServerId}/instructor-dashboard?viewerUserId=`
- Orchestrates community, message, and agent metric endpoints

## 2. Supporting Metric Endpoints

- Community: `GET .../instructor-dashboard/community-metrics`
- Message: `POST /api/v1/instructor-dashboard/message-metrics`
- Agent: `GET .../ai-usage-metrics`

## 3. Gateway And Frontend Demo

- Gateway routes for analytics dashboard and message instructor-dashboard paths
- Demo panel: **Instructor Dashboard (#23)**

## Verification

```bash
mvn -pl analytics-service,community-service,message-service,agent-service verify
npm run lint && npm run build
```

## Deferred

- Event-driven read models and Kafka ingestion deferred; MVP uses pull-based aggregation.
- `TODO(#auth)` caller `viewerUserId` until #30.
