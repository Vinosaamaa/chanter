# realtime-service

WebSocket fan-out for Study Server and Course text channels.

## Local run

```bash
make backend-realtime
```

Requires `CHANTER_JWT_SECRET` (32+ chars), community-service on `:8082`, and message-service on `:8083`.

## WebSocket protocol

Connect to `GET /api/v1/realtime/ws?access_token=<jwt>` (or through gateway with `Authorization: Bearer` on the upgrade).

Client frames:

```json
{ "type": "subscribe", "channelId": "...", "channelScope": "STUDY_SERVER" | "COURSE" }
{ "type": "unsubscribe" }
{ "type": "send", "channelId": "...", "channelScope": "...", "body": "hello" }
```

Server frames:

```json
{ "type": "subscribed", "channelId": "...", "channelScope": "COURSE" }
{ "type": "message", "channelId": "...", "channelScope": "COURSE", "payload": { ... } }
{ "type": "error", "code": "forbidden", "message": "..." }
```

Message history and durable writes use message-service REST (`/api/v1/*/messages`).
