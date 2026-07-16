# Issue #186 change log — Require Google OAuth `email_verified` (SEC-05)

**Branch:** `cursor/186-oauth-email-verified-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-05

## Summary

Google OAuth no longer links or provisions Chanter accounts from userinfo email unless `email_verified` is true. Already-linked Google `sub` values may still sign in (stable identity). Unverified / missing claims return **403**.

## Changes

| Area | Change |
|------|--------|
| `OAuthAuthService` | Extract `sessionFromGoogleUserInfo`; `requireGoogleEmailVerified` before email provision/link |
| `OAuthEmailVerifiedSmokeTest` | Unverified rejection; verified provision; link + mark verified; linked subject bypass |

## Tests

```bash
cd backend && mvn -B -pl auth-service -am test -Dtest=OAuthEmailVerifiedSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
```
