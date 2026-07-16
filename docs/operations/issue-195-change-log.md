# Issue #195 Change Log — BUG-01: Refresh access token on WebSocket reconnect

## Problem

`RealtimeClient` and `SocialRealtimeClient` captured `accessToken` at construction and reused it on every reconnect. An idle socket whose JWT expired would reconnect forever with the stale token until an unrelated HTTP 401 refreshed the session — realtime delivery stalled silently.

## Changes

- Both clients now take `getAccessToken` + `refreshSession` instead of a fixed token string.
- On reconnect (`reconnectAttempts > 0`), call `refreshSession()` then open the socket with the latest token from `getAccessToken()`.
- Shared `getApiAccessToken()` / `refreshApiSession()` in `api-client.ts` (single-flight) used by channel + friends hooks.
- Unit tests cover refresh-before-reconnect for both clients.

## Acceptance

- [x] Regression test: reconnect refreshes and uses the new token
- [ ] Idle WS session survives token expiry via refresh (browser smoke after merge)
- [ ] CI green + CodeAnt
