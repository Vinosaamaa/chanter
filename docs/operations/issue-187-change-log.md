# Issue #187 change log — Allow public auth paths through gateway (SEC-07 / BUG-02)

**Branch:** `cursor/187-gateway-public-auth-paths-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-07 / BUG-02

## Summary

Gateway JWT filter allow-list now includes forgot/reset/verify-email and all `/api/v1/auth/oauth/**` routes so password-reset, email verification, and OAuth work through the gateway without a bearer token. `/api/v1/auth/me` and `/api/v1/auth/profiles/query` remain protected.

## Changes

| Area | Change |
|------|--------|
| `JwtAuthenticationGlobalFilter` | Add public auth paths + `oauth/` prefix |
| `JwtAuthenticationGlobalFilterPublicAuthPathsTest` | Allow-list + 401 for `/me` |

## Tests

```bash
cd backend && mvn -B -pl gateway-service -am test \
  -Dtest=JwtAuthenticationGlobalFilterPublicAuthPathsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
