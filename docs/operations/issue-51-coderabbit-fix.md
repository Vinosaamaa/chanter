# Issue #51 CodeRabbit Fix Log

PR: [#68](https://github.com/Vinosaamaa/chanter/pull/68)

## Fixed

| Comment | Fix |
|---------|-----|
| Scope `access_token` to realtime handshakes only | `JwtAuthenticationGlobalFilter` accepts query token only under `/api/v1/realtime/**` |
| Strip `access_token` before proxying | Gateway removes query param after resolving `X-User-Id` |
| Gateway realtime route URI scheme | Default `REALTIME_SERVICE_URL` uses `ws://localhost:8087` |
| 201 `Location` without GET route | Channel message POST returns `201` body without `Location` |
| Unbounded message body | `CreateChannelMessageRequest` adds `@Size(max = 4000)` |
| Community access client timeouts / 5xx mapping | `HttpChannelMessageAccessClient` uses connect/read timeouts and maps transport/5xx errors |
| `since` pagination boundary drops | Composite cursor via `afterMessageId` + `ORDER BY created_at, id` |
| Missing study-server smoke tests | Added post/list + forbidden tests for study-server channels |
| Test message client trims body | `TestChannelMessageClient` forwards body unchanged |
| Subscription hub race on unsubscribe | `computeIfPresent` removes empty channel sets atomically |
| Fan-out failure aborts publish | Per-session `onErrorResume` + stale subscription cleanup |
| Send path fails on fan-out error | `handleSend` swallows publish fan-out errors after persistence |
| WS smoke test receive ordering | Cache inbound flux before subscribe/send in both tests |
| Silent send when socket closed | `RealtimeClient.sendFrame` throws; hook returns `false` and keeps draft |
| History load races realtime message | Initial fetch merges into existing state |
| Send failure clears draft | `ChannelConversation` clears input only when `sendMessage` returns true |
| Stale "Realtime connection failed" banner | Disconnect clears handlers; clear connection errors on `connected` |
| `make infra-up` builds broken realtime image | Start only postgres/redis/redpanda/minio |
| macOS Java 17 vs 21 mismatch | Makefile forces Java 21 when installed |
| Docker realtime build missing Maven | Dockerfile uses `maven:3.9-eclipse-temurin-21` build stage |
| Realtime container runs as root | Runtime image uses dedicated `chanter` user |
| `host.docker.internal` on Linux | `extra_hosts: host-gateway` on realtime-service |

## Deferred

| Comment | Reason |
|---------|--------|
| — | None |

## Second pass (post `a2dd175`)

| Comment | Fix |
|---------|-----|
| `afterMessageId` without `since` | `ChannelMessageService` rejects cursor-only requests with `400` |
| Stale subscription clears newer session | `removeSubscription` uses conditional `computeIfPresent` |
| macOS Makefile silent Java fallback | `$(error ...)` when neither Java 21 nor 23 is installed |
| Unexpected WS downstream failures close socket | `handleSubscribe` / `handleSend` return error frames for non-`ResponseStatusException` failures |
| Missing resume-cursor smoke test | Added `courseChannelMessagesCanResumeAfterCursor` |
| Oversized frontend send shows optimistic row | Client rejects bodies over 4000 chars before optimistic append |
| Community 5xx not mapped to `BAD_GATEWAY` | Catch `HttpServerErrorException` explicitly |
| WS smoke test misses first frame | Use `replay().autoConnect(1)` + `Mono.when` before send |
