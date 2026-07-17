# Issue #201 Change Log — SEC-20: Bound auth rate limiter memory

## Problem

`AuthRateLimiter` stored every client key in an unbounded `ConcurrentHashMap`, so a flood of distinct keys could grow memory without bound.

## Changes

- Evict counters whose rate-limit window has expired on each `check()`.
- Cap distinct keys with `chanter.auth.rate-limit.max-entries` (default 10_000; env `CHANTER_AUTH_RATE_LIMIT_MAX_ENTRIES`).
- When over the cap, drop the oldest windows after expired eviction.
- Unit tests cover limit enforcement, entry cap, and TTL eviction.

## Acceptance

- [x] Map does not grow without bound
- [x] Expired windows are removed
- [x] Unit tests pass
- [ ] CI + CodeAnt
- [ ] Login still works (browser/API)

## Verification

```bash
cd backend
unset CHANTER_JWT_SECRET CHANTER_INTERNAL_SERVICE_TOKEN
mvn -pl auth-service -am test -Dtest=AuthRateLimiterTest -Dsurefire.failIfNoSpecifiedTests=false
```
