# Issue #184 change log — Stop persisting refresh token in localStorage (SEC-06)

**Branch:** `cursor/184-refresh-token-storage-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-06

## Summary

`refreshToken` remains in the Zustand auth store for in-tab refresh and logout revoke, but is **no longer written** to `localStorage` (`chanter-auth`). Persist keeps only the short-lived `accessToken` and `user` so a reload can restore the session within the access TTL. A persist `version`/`migrate` strips any legacy refresh token from older stored blobs.

HttpOnly cookie-based refresh would need auth-service Set-Cookie + gateway CORS credentials; deferred as a follow-up. Memory-only refresh matches the issue’s documented fallback.

## Changes

| Area | Change |
|------|--------|
| `auth-store.ts` | `partialize` omits `refreshToken`; persist `version: 1` + `migrate` strips legacy |
| `auth-store.test.ts` | Asserts storage never contains refresh; clearSession; migrate legacy |

## Tradeoffs

- **In-tab:** 401 refresh and logout revoke still work (refresh held in memory).
- **Full reload after access expiry:** user must sign in again (no durable refresh). Within access TTL (~15m), access + user restore from `localStorage`.
- **Residual:** XSS can still read short-lived access from `localStorage`; cannot steal a renewable refresh from storage.

## Tests

```bash
cd frontend && npm test -- --run src/stores/auth-store.test.ts
```
