# Issue #189 change log — Fix auth rate limiter client IP behind gateway (SEC-08)

**Branch:** `cursor/189-auth-rate-limiter-ip-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-08

## Summary

Auth rate-limit keys no longer use only `request.getRemoteAddr()` (the gateway hop for every client). Client IP is taken from the first `X-Forwarded-For` hop, with remote-addr fallback. Login keys are `login:<email>:<ip>` so one client cannot exhaust a shared IP bucket for unrelated accounts as easily. `server.forward-headers-strategy: framework` is enabled on auth-service.

## Changes

| Area | Change |
|------|--------|
| `ClientIpResolver` | First `X-Forwarded-For` hop |
| `AuthController.rateKey` | Uses resolver; login includes normalized email |
| `application.yml` | `forward-headers-strategy: framework` |
| `ClientIpResolverTest` | Forwarded / fallback cases |

## Tests

```bash
cd backend && mvn -B -pl auth-service -am test -Dtest=ClientIpResolverTest -Dsurefire.failIfNoSpecifiedTests=false
```
