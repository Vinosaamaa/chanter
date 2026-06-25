# Issue #51 Change Log

Issue: [#51 Bootstrap Realtime Service And Live Course Channel Chat](https://github.com/Vinosaamaa/chanter/issues/51)

## Summary

Bootstrapped `realtime-service` with authenticated WebSocket subscribe/send/fan-out, durable channel messages in message-service, and a live conversation column in the production app shell.

## Backend

### realtime-service (`:8087`)

| Piece | Purpose |
|-------|---------|
| `RealtimeWebSocketHandler` | JWT/`X-User-Id` auth, subscribe/unsubscribe/send frames |
| `RealtimeSubscriptionHub` | In-memory channel fan-out to subscribed sessions |
| `HttpChannelSubscriptionAuthorizer` | Community-service permission checks before subscribe |
| `HttpChannelMessageClient` | Persists sends via message-service REST |
| `RealtimeWebSocketSmokeTest` | Subscribe/send/receive + forbidden subscribe |

WebSocket path: `GET /api/v1/realtime/ws?access_token=<jwt>`

### message-service

| Endpoint | Purpose |
|----------|---------|
| `GET/POST /api/v1/study-server-channels/{id}/messages` | History + durable write (study server text channels) |
| `GET/POST /api/v1/course-channels/{id}/messages` | History + durable write (course text channels) |

### community-service

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/study-server-channels/{id}/channel-message-access` | Study-server member + text channel gate |
| `GET /api/v1/course-channels/{id}/channel-message-access` | Enrollment/instructor text channel gate |

### gateway-service

- Routes `/api/v1/realtime/**` → realtime-service
- Routes channel message REST before community catch-all
- Accepts `access_token` query param on `/api/v1/*` for WebSocket upgrades

### Docker Compose

- `realtime-service` image build (`infra/docker/realtime-service/Dockerfile`) with health check

## Frontend

| Path | Purpose |
|------|---------|
| `features/realtime/realtime-client.ts` | WebSocket client with reconnect + resubscribe |
| `features/shell/components/ChannelConversation.tsx` | Live timeline + composer (replaces placeholder) |
| `features/shell/hooks/use-channel-conversation.ts` | History load, optimistic send, missed-event reconcile |
| `vite.config.ts` | `ws: true` on `/api` dev proxy |

## Verification

```bash
cd backend && mvn -pl community-service,message-service,realtime-service,gateway-service -am test
cd frontend && npm run lint && npm run build
make backend-gateway backend-community backend-message backend-realtime backend-auth   # separate terminals
make frontend-dev
```

Manual:

1. `/dev/demo` → create study server + course + enrollment
2. **Open app shell as Owner** or **Learner**
3. Open `#general` (owner) or `#questions` (learner)
4. Send a message — appears without refresh; second browser/tab receives fan-out

## Follow-ups

- #52 — `#questions` AI context panel
- Redis/broker-backed fan-out when scaling beyond single realtime node
- Display names instead of raw user ids in message timeline
