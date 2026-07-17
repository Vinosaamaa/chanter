# Issue #202 Change Log — SEC-21: Reject blank internal service token at startup

## Problem

Constant-time compare of an empty configured `chanter.internal-service-token` against an
empty presented token succeeds (`MessageDigest.isEqual`). A misconfigured empty token
would authenticate as an internal service.

## Changes

- Added `InternalServiceTokens` in `common` — rejects blank, short (&lt;32), and the known
  SEC-04 default at load time.
- Wired `require` / `requireBytes` into auth/notification/community/media/message/agent/
  search/analytics/realtime constructors that inject the internal token.
- Removed empty default on media `chanter.agent-service.service-token`.
- Unit tests for blank / short / default rejection.

## Acceptance

- [x] Blank configured token fails startup (constructor)
- [x] Min length 32 enforced (aligned with `make product-env`)
- [x] Known default rejected
- [x] Unit + smoke tests pass with valid test tokens
- [ ] CI + CodeAnt
- [ ] Product stack still boots with `make product-env` secrets

## Verification

```bash
cd backend
unset CHANTER_JWT_SECRET CHANTER_INTERNAL_SERVICE_TOKEN
mvn -pl common -am test -Dtest=InternalServiceTokensTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl auth-service,notification-service,community-service -am test \
  -Dtest=AuthSessionSmokeTest,AuthenticatedUserFilterSmokeTest,NotificationSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
