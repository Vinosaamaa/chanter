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
| P1: Call state-transition race (accept vs end/timeout) | Fixed — atomic `tryCreateRingingCall`, `activateIfRinging`, `endIfPresent` / `endIfRinging` on store |
| P1: Caller can accept own invite | Fixed — `activateIfRinging` requires callee user id |
| P1: Busy detection race on concurrent invites | Fixed — atomic `tryCreateRingingCall` with per-user index |
| P1: Blocking `WebClient.block()` on media-token path | Fixed — reactive `Mono` end-to-end (client, hub, controller) |
| P1: Media-token REST trusts spoofable `X-User-Id` | Fixed — `AuthenticatedUserWebFilter` validates JWT `Authorization` bearer |
| P1: Internal community DM token endpoint unauthenticated | Deferred — local product stack binds services to localhost; add shared internal service credential in horizontal-scale hardening |
| P2: Call error hidden when overlay closes on `ended` | Fixed — keep overlay visible through `ended` when `callError` set; longer reset delay on failures |
| P2: Double-click Call sends duplicate invites | Fixed — `inviteInFlightRef` guard + disable when not `idle` |
| P2: `ScheduledExecutorService` leak | Fixed — `@PreDestroy shutdownScheduler()` |
| P2: `REALTIME_SERVICE_HTTP_URL` env split | Deferred — document in change log; gateway HTTP target matches WS host in default local config |
| P2: Missing early `calleeFailure` check in call smoke | Fixed |
| P2: `WebClientRequestException` → generic 500 | Fixed — map to `502 BAD_GATEWAY` |
| P2: DM LiveKit token allows unrestricted publish | Fixed — `CanPublishSources(microphone)` for DM calls |
| P2: O(n) active-call scan | Fixed — per-user active call index in store |
| P2: Test `SocialGraph.block` leaves friendship edge | Fixed — remove friendship + filter blocked friends in listings |
| P2: `resetCall` timer clobbers next call | Fixed — clear pending timer before scheduling |
| P2: LiveKit error retriggers websocket reconnect | Fixed — `livekitErrorRef` decoupled from `handleCallEvent` deps |
| P3: Call overlay missing dialog a11y | Fixed — `role="dialog"` + `aria-modal` |
| P3: Mute toggle unhandled rejection | Fixed — `.catch` surfaces `callError` |
| P3: Duplicate session delivery loops in `SocialRealtimeHub` | Deferred — refactor to shared helper in follow-up |
| P3: DM send uses call-specific error copy | Fixed — restore message-specific validation in `sendDirectMessage` |
| P3: `SocialCallMessage` not composed into union type | Deferred — client already widens union; centralize in types polish pass |
| P3: Hardcoded `authenticatedUserId` attribute key | Fixed — `AuthRequestAttributes.USER_ID` |
| P3: Test authorizer forbidden copy drift | Fixed — align with message-service wording |
| P3: Stale `callError` on new ring | Fixed — clear on `call_ringing` |

## Pass 3

| Comment | Action |
|---------|--------|
| P0: Ring timeout drops `call_ended` after `endIfRinging` | Fixed — deliver ended events directly from timeout handler |
| P1: `inviteInFlightRef` stuck when `inviteCall` throws | Fixed — try/catch resets in-flight flag |
| P3: Duplicate LiveKit token builders | Deferred — share builder in voice hardening pass |
