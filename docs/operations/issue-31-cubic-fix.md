# Issue #31 — cubic fix log

> Record every cubic Dev AI review pass here. One row per finding; note fix or explicit deferral.

## Pass 1

| Comment | Action |
|---------|--------|
| CI: `sentAt` nanosecond vs microsecond mismatch in DM smoke test | Fixed — truncate to `ChronoUnit.MICROS` in `SocialMessagingService.sendDirectMessage` |
| CI: `react-hooks/set-state-in-effect` on friends hub message load | Fixed — derive `isLoadingMessages` from `loadedMessagesFriendId` vs `selectedFriendId` |
| P2: Failed DM history leaves endless loading spinner | Fixed — on fetch error, set `loadedMessagesFriendId` to selected friend with empty messages |
| P2: Optimistic send while thread loading attaches to wrong friend cache | Fixed — claim thread ownership on send; guard realtime merges with refs |

## Pass 2

| Comment | Action |
|---------|--------|
| P1: `realtime-service` pom dropped actuator | Fixed — restored `spring-boot-starter-actuator` alongside Redis |
| P1: Blocking Redis/HTTP on WebFlux event loop in `SocialRealtimeHub` | Fixed — offload connect/disconnect/DM/presence to `Schedulers.boundedElastic()` |
| P1: Fire-and-forget `disconnect().subscribe()` in handler | Fixed — chain `disconnect` after receive loop via `Mono.defer` |
| P1: WebSocket reconnects on every friend selection | Fixed — keep socket effect keyed on `accessToken` only; use `selectedFriendIdRef` in callbacks |
| P2: `HttpCoMembershipClient` missing timeouts / null body → false | Fixed — JDK client timeouts; `BAD_GATEWAY` on empty body |
| P2: `HttpSocialFriendsClient` missing `WebClientResponseException` handling | Fixed — map to `ResponseStatusException` like sibling clients |
| P2: Co-membership RPC before cheap local friend-request rejections | Fixed — local block/friend/pending checks first |
| P2: `TestDirectMessageClient` allows self-DM and untrimmed body | Fixed — mirror message-service validation |
| P2: `SocialGraph.befriend` allows self-friend | Fixed — reject identical UUIDs |
| P2: Redis presence keys never expire | Partial — 2-minute TTL on `markOnline` (heartbeat refresh on connect) |
| P2: Friend-list load failure shown as empty state | Fixed — show `hub.error` in sidebar when friends fetch fails |
| P2: HANDOFF startup prompt still references #61 | Fixed — #31 / PR #79 / branch name |
| P2: Disconnect race can drop replacement socket | Fixed — `sessionsByUser.remove(userId, userSessions)` conditional remove |
| P3: Shared base layout for friends vs shell chrome | Deferred — follow-up refactor; layouts intentionally minimal for #31 slice |
| P2: Multi-instance DM/presence fan-out needs Redis pub/sub | Deferred — single-instance product stack for #31; note for horizontal scale |
| P2: Per-session Redis presence leases for multi-instance | Deferred — TTL mitigates stale keys; session-scoped leases are #63+ hardening |
| P2: Co-membership query needs user-leading indexes | Deferred — acceptable at MVP scale; add migration when membership grows |
| P2: `friendsSince` uses request `created_at` not accept time | Deferred — schema change (`accepted_at`) out of #31 scope |
| P2: WS token in query string | Deferred — matches existing realtime auth pattern; ticket handshake is follow-up |
| P2: Linear reconnect backoff without jitter | Deferred — minor; exponential backoff in hardening pass |
| P2: No unread badge for non-selected friend DMs | Deferred — MVP UX gap; tracked for polish |
| P2: WS DMs dropped while selected thread still loading | Fixed — claim thread and append on matching `dm_message` before REST load completes |
| P2: WS send lacks reconciliation fallback when echo missing | Fixed in Pass 4 — ack correlation on `sendDirectMessage` |
| P3: `ConnectionBadge` string typing / `Intl` per message | Deferred — low impact at MVP message volume |

Verification: `npm run lint`, `FriendRequestAndDirectMessageSmokeTest`, `SocialRealtimeWebSocketSmokeTest`, CI on PR #79.

## Pass 3

| Comment | Action |
|---------|--------|
| P0: `disconnect` skipped on WebSocket error path | Fixed — `materialize()` + always run `disconnect` on terminal signal |
| P1: Friends lookup failure aborts WS after mark-online | Fixed — `HttpSocialFriendsClient` returns empty list on transport errors |
| P2: Co-membership `RestClientException` → opaque 500 | Fixed — map to `BAD_GATEWAY` |
| P2: Stale error when switching friends after failed load | Fixed — clear error on friend select + successful fetch |
| P2: Draft cleared while new text typed during send | Fixed — only clear if draft still matches submitted text |
| P3: Conversation error missing `role="alert"` / input label | Fixed |
| P2: 2-minute presence TTL caused false offline | Reverted TTL (single-instance MVP) |
| P2: Multi-instance Redis presence / connect cleanup race | Deferred — documented in Pass 2 |
| P2: Co-membership inside `@Transactional` | Deferred — acceptable for MVP volume |

## Pass 4

| Comment | Action |
|---------|--------|
| P2: WS send returns success before server ack | Fixed — `sendDirectMessage` awaits `dm_message` echo or error frame with timeout |
| P2: REST send refresh can write to wrong friend after switch | Fixed — guard post-await state updates with `selectedFriendIdRef` |
| P2: Initial presence snapshot failure drops whole socket | Fixed — `sendInitialFriendPresence` is best-effort (`onErrorResume`) |
| P2: Long DM thread pushes composer off-screen | Fixed — `min-h-0` on scrollable message list |
| P3: Invalid `sentAt` crashes render | Fixed — guard `formatTimestamp` like channel conversation |

## Pass 5

| Comment | Action |
|---------|--------|
| P2: WS ack matched only on recipient+body (cross-tab false positive) | Fixed — `clientMessageId` on `send_dm` echoed in `dm_message` payload |
| P2: Fix log claimed echo reconciliation without REST fallback | Fixed — HTTP send+refresh fallback when WS ack fails |
| P3: Initial presence errors swallowed silently | Fixed — warn log before continuing degraded connect |

## Pass 6

| Comment | Action |
|---------|--------|
| P2: Stale history fetch overwrites live/optimistic DMs | Fixed — merge history into same-thread cache when messages already exist |
| P2: REST fallback fails when refresh fails after successful POST | Fixed — `reconcileThreadMessages` merges POST response; refresh errors are non-fatal |
| P1: Cancelled WebSocket skips social disconnect | Fixed — `Mono.usingWhen` async cleanup on complete/error/cancel |
| P2: Social connect blocks before scheduler boundary | Fixed — defer connect/presence onto `boundedElastic` |
| P3: Shared friends/shell layout duplication | Deferred — follow-up refactor |

## Deferred

See Pass 2 table rows marked **Deferred**.
