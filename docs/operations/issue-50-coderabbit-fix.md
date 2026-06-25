# Issue #50 CodeRabbit Fix Log

PR: [#67](https://github.com/Vinosaamaa/chanter/pull/67)

## Fixed

| Comment | Fix |
|---------|-----|
| List vs navigation access mismatch | Tightened `listAccessibleStudyServers` to owner role, instructor role, or cohort enrollment only (matches `findViewerScope`) |
| Navigation service access alignment | Same predicate set via shared repository methods; added `canViewFullCatalog` on navigation response |
| Sidebar "My courses" for owners | Label switches to **Courses** when `canViewFullCatalog` is true |
| Conversation placeholder states | Separate loading, denied, and unknown-channel copy for deep links |
| Server redirect gated on list query | `/app/servers/:serverId` redirect waits only on navigation query |
| Gateway bypass paths spoof `X-User-Id` | Strip inbound `X-User-Id` on OPTIONS, public, and non-API paths |
| Demo auth fetch throws on cold start | `loginWithRetry` catches network errors and backs off |
| `.env.example` API base key | Document `VITE_API_BASE` (dev defaults to Vite proxy when unset) |
| Makefile JWT length | `require-jwt-secret` enforces 32+ characters |

## Deferred

| Comment | Reason |
|---------|--------|
| Non-owner instructor smoke test | No API to assign a co-instructor without creating the course as that user; owner-as-instructor path already covered |
| Refresh token not persisted for logout | Intentional #49 security choice — refresh token kept in memory only; logout revokes when token is still in session; document follow-up for server-side "revoke current session" |
