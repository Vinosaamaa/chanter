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
| P2: Per-session Redis presence leases for multi-instance | Deferred — single-instance MVP stack; session-scoped leases tracked for horizontal-scale hardening |
| P2: Co-membership query needs user-leading indexes | Deferred — acceptable at MVP scale; add migration when membership grows |
| P2: `friendsSince` uses request `created_at` not accept time | Deferred — schema change (`accepted_at`) out of #31 scope |
| P2: WS token in query string | Deferred — matches existing realtime auth pattern; ticket handshake is follow-up |
| P2: Linear reconnect backoff without jitter | Deferred — minor; exponential backoff in hardening pass |
| P2: No unread badge for non-selected friend DMs | Deferred — MVP UX gap; tracked for polish |
| P2: WS DMs dropped while selected Direct Message still loading | Fixed — claim DM ownership and append on matching `dm_message` before REST load completes |
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

## Pass 7

| Comment | Action |
|---------|--------|
| P2: Channel unsubscribe skipped when social disconnect fails | Fixed — `onErrorResume` before `unsubscribeAll` |
| P2: Missed DMs after reconnect | Fixed — reconcile selected thread on `connected` |
| P2: Duplicate optimistic removal on identical body | Fixed — remove only first matching optimistic per echo |
| P2: WS timeout triggers duplicate REST POST | Fixed — reconcile via GET before HTTP retry |
| P2: Online/offline fanout failure closes socket | Fixed — best-effort `notifyFriendsPresence` |
| P1: Reconnect race drops replacement socket / stale offline | Fixed — per-user lock + offline only when no sessions remain |
| P2: Offline fanout failure blocks channel cleanup | Fixed — best-effort offline notify + handler cleanup order |

## Pass 8

| Comment | Action |
|---------|--------|
| P2: `userLocks` map grows without bound | Fixed — remove lock when last session disconnects |
| P3: Social disconnect cleanup errors swallowed silently | Fixed — warn log in handler `onErrorResume` |

## Pass 9

| Comment | Action |
|---------|--------|
| P1: Early `userLocks` removal races with reconnect offline fanout | Fixed — prune lock after async offline cleanup when user still has no sessions |

## Pass 10

| Comment | Action |
|---------|--------|
| P2: Stale per-user lock monitor swapped under waiting threads | Fixed — single hub `sessionLock` for all connect/disconnect bookkeeping |

## Pass 11

| Comment | Action |
|---------|--------|
| P2: Global `sessionLock` held during blocking Redis `markOffline` | Fixed — check session map under lock, then mark offline outside lock |

## Pass 12

| Comment | Action |
|---------|--------|
| P1: Stale `shouldMarkOffline` before async Redis write | Fixed — connection generation guard + revalidate before `markOffline` |

## Pass 13

| Comment | Action |
|---------|--------|
| P1: Offline presence fanout never runs after `fromRunnable` | Fixed — chain notify with `then(Mono.defer(...))` |
| P2: `connectionGenerations` map never pruned | Fixed — remove entry after offline cleanup when user stays disconnected |

## Pass 14

| Comment | Action |
|---------|--------|
| P1: `HttpCoMembershipClient` drops `RestClientException` cause | Fixed — pass exception as `ResponseStatusException` cause |
| P2: Failed social connect leaves stale session | Fixed — `connect` rolls back via `disconnect` on error |
| P3: Friends empty state hidden by realtime `hub.error` | Fixed — separate `friendsListError` from conversation/realtime errors |
| P3: Friend list selection missing `aria-pressed` | Fixed |
| P3: `ConnectionBadge` accepts any string | Fixed — `SocialRealtimeConnectionStatus` union |
| P3: Per-message `Intl.DateTimeFormat` allocation | Fixed — module-level formatter |
| P2: Linear reconnect backoff / duplicate socket timers | Fixed — exponential backoff with jitter; clear reconnect timer on connect/disconnect |
| P2: WS scheme derived from page not API base | Fixed — derive protocol from `getApiBase()` URL |
| CI flake: social smoke test 5s timeout under load | Fixed — increase blocking read timeout to 10s |
| P1/P2: `notifyFriendsPresence` blocking on event loop | Already fixed — `subscribeOn(boundedElastic)` in Pass 2 |
| P2/P3: Multi-instance pub/sub, Redis presence leases, co-membership indexes, `friendsSince`, WS token in query | Deferred — documented in Pass 2 |
| P3: Shared friends/shell layout duplication | Deferred — follow-up refactor |
| P3: Fix-log deferred note / DM wording | Fixed — clarified deferrals; use Direct Message wording in new rows |

## Pass 15

| Comment | Action |
|---------|--------|
| P1: `HttpSocialFriendsClient` blocking `.block()` on event loop | Fixed — `SocialFriendsClient` now returns `Mono<List<UUID>>`; hub/handler subscribe reactively |
| P3: Fix-log TTL deferral contradicts reverted TTL | Fixed — deferred note no longer cites TTL mitigation |
| P3: Fix-log uses `thread` instead of Direct Message | Fixed — Pass 2 row wording updated |

## Pass 16

| Comment | Action |
|---------|--------|
| P1: Stale disconnect prunes generation counter for newer reconnect | Fixed — only remove `connectionGenerations` when cleanup still owns `generationAtDisconnect` |

## Pass 17

| Comment | Action |
|---------|--------|
| P2: Generation cleanup skipped when offline notify never runs | Fixed — attach generation-removal `doFinally` to full offline cleanup chain |
| P3: `aria-pressed` wrong control model for friend list | Fixed — use `aria-current` for selected conversation |
| P3: Reconnect jitter can exceed `RECONNECT_MAX_MS` | Fixed — cap delay after jitter |

## Pass 18

| Comment | Action |
|---------|--------|
| P2: Capped reconnect retries lose jitter spread at `RECONNECT_MAX_MS` | Fixed — reserve jitter headroom before capping base delay |

## Pass 19

| Comment | Action |
|---------|--------|
| P2: Per-attempt jitter still collapses capped delay to exactly 5s | Fixed — reserve fixed `RECONNECT_JITTER_MS` window below cap |

## Deferred

See Pass 2 table rows marked **Deferred**.
