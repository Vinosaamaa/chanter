# Issue #190 Change Log — SEC-09: OAuth state + PKCE

## Summary

Adds CSRF protection (`state`) and PKCE (`code_challenge` / `code_verifier` S256) to the
Google OAuth 2.0 login flow.

## Problem

The previous implementation generated an authorization URL with no `state` or PKCE parameters.
A malicious page could forge a callback request with a valid code obtained from a legitimate
Google authorization, bypassing the origin-binding requirement (SEC-09).

## Changes

### Backend — auth-service

| File | Change |
|------|--------|
| `OAuthPendingStore.java` (new) | In-memory `ConcurrentHashMap` keyed by `state`. Each entry holds `codeVerifier`, `provider`, and `createdAt`. TTL = 10 min; expired entries purged lazily on `create()`. Single-use via `consume()`. |
| `OAuthAuthService.java` | `authorizationUrl()` now calls `pendingStore.create()` and adds `state`, `code_challenge` (S256), and `code_challenge_method=S256` to the Google URL. `completeGoogleLogin(code, state)` now validates `state` against the store and includes `code_verifier` in the token exchange form. Missing/blank state → 400; unknown/expired state → 403. |
| `AuthController.java` | `OAuthCodeRequest` record gains `@NotBlank String state`. `googleCallback` passes `request.state()` to `completeGoogleLogin`. |

### Frontend

| File | Change |
|------|--------|
| `auth-api.ts` | `completeGoogleOauth(code, state)` now accepts both parameters and sends `{ code, state }` to the backend. |
| `OAuthCallbackPage.tsx` | Reads `state` from query params alongside `code`. Passes both to `completeGoogleOauth`. Shows error if either is missing from redirect. |

### Tests

`OAuthStatePkceTest.java` (new) covers:
- `OAuthPendingStore` unit tests: distinct state/verifier per call, single-use, blank/null/expired
  state handling
- `authorizationUrl` generates URL with `state`, `code_challenge`, `code_challenge_method=S256`
- `completeGoogleLogin` with null/blank state → 400; unknown state → 403

## Security invariants preserved

- **SEC-05 email_verified**: `sessionFromGoogleUserInfo` logic is unchanged; the state/PKCE
  check occurs before profile fetch.
- **SEC-09 state/PKCE**: Every authorization URL carries a unique state. The callback rejects
  any request whose state is not in the pending store or has expired.

## Deployment notes

- No database migrations required (store is in-memory).
- A service restart during an active OAuth flow will expire any pending entries; users will
  be redirected back to sign-in as the callback returns 403.
- For multi-instance deployments, replace `OAuthPendingStore` with a shared cache (e.g. Redis)
  before scaling horizontally.
