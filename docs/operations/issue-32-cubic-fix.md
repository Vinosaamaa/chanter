# Issue #32 — cubic fix log

> Record every cubic Dev AI review pass here. One row per finding; note fix or explicit deferral.

## Pass 1

| Comment | Action |
|---------|--------|
| P2: Concurrent initial presence writes on same WebSocketSession — use `concatMap` | Fixed — serialize `sendJson` in `sendInitialFriendPresence` |
| CI: `SocialRealtimeWebSocketSmokeTest` flakes after DM call tests | Fixed — run social smoke first (`@Order`), clear call store, hang up in call smoke, extend waits, offload presence snapshot to bounded elastic |

## Pass 2

| Comment | Action |
|---------|--------|
| (awaiting re-review) | — |
