# Issue #143 — Durable inbox / notifications

**Branch:** `cursor/143-inbox-notifications-unread-6106`

## Summary

- Bootstrapped `backend/notification-service` (port `8089`, DB `chanter_notification`) with Flyway `notifications` table, JWT user filter for `/api/v1/me/notifications/**`, and internal create API secured by `X-Chanter-Internal-Service-Token`.
- Public APIs: list/filter/status, unread-count, mark read, mark done. Internal create is idempotent on `(user_id, source_type, source_id, kind)`.
- Gateway routes `/api/v1/me/notifications/**` to notification-service at order `-2` (ahead of community `/api/v1/me/**`).
- Producers (best-effort, never fail primary action):
  - **message-service:** support-question human reply and `AI_ANSWERED` status → notify question author (`SUPPORT_QUESTION_ANSWERED`).
  - **community-service:** announcement publish → fan-out to study-server members (cap 200); community event create → members/cohort enrollments; office-hours schedule → cohort enrollments.
- Frontend: `features/inbox` API + hooks; `InboxPage` loads durable notifications, Mentions/Announcements filters, mark read/done, Open via `href`; reply composer removed. Bell (top bar) and Inbox sidebar badge use real unread-count (no hard-coded badges).

## Verification

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
(cd backend && mvn -B -pl notification-service,message-service,community-service -am test)
(cd frontend && npm test -- --run src/features/inbox src/features/v2-shell/pages/InboxPage.test.tsx src/features/v2-shell/components/V2Sidebar.test.tsx)
```

## Local run notes

1. Ensure Postgres has `chanter_notification` (fresh `infra/postgres/init` or `CREATE DATABASE chanter_notification;`).
2. Set `CHANTER_INTERNAL_SERVICE_TOKEN` for notification, message, and community services.
3. `make backend-notification` (port 8089) and restart gateway.

## Deferred / follow-ups

- Async / Kafka fan-out for large hubs (sync RestClient MVP only).
- Richer deep links for support questions (study-server resolution beyond `/app/inbox?channelId&questionId`).
- SUPPORT_QUESTION_CREATED instructor notifications (kind reserved; not produced yet).
- Realtime unread badge push (poll every 30s for now).
