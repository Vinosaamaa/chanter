# Issue #183 change log — Authenticate internal DM-call LiveKit token minting (SEC-03)

**Branch:** `cursor/183-dm-call-internal-token-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-03

## Summary

`POST /internal/v1/dm-calls/{callId}/media-token` now requires `X-Chanter-Internal-Service-Token` (constant-time compare) and proves the minting user is a call participant via `X-Dm-Call-Caller-Id` / `X-Dm-Call-Callee-Id` (must match `X-User-Id`). Realtime already gates non-participants before calling community; it now forwards the service token and party headers.

## Changes

| Area | Change |
|------|--------|
| `InternalDmCallController` | Service-token auth + participant header check |
| `community-service` `application.yml` / test yml | `chanter.internal-service-token` |
| `HttpDmCallMediaTokenClient` | Sends internal token + caller/callee headers |
| `DmCallMediaTokenClient` / test stub / `DirectMessageCallHub` | Pass caller/callee into mint call |
| `realtime-service` `application.yml` | `chanter.community-service.service-token` |
| Smoke tests | Missing/wrong token → 401; non-participant → 403; happy path → 200 |

## Tests

```bash
cd backend && mvn -B -pl community-service,realtime-service -am test \
  -Dtest=InternalDmCallMediaTokenSmokeTest,DirectMessageCallSignalingSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Notes

Community does not persist DM-call membership; realtime is the source of truth for active calls. Participant proof is therefore the party headers supplied by realtime after its own hub check, plus the shared internal service token so the endpoint is not publicly mintable.
