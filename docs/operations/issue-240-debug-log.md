# Issue #240 debug log: Cross-account route and query leakage

**Date:** 2026-08-09
**Issue:** [#240](https://github.com/Vinosaamaa/chanter/issues/240)

## 1. Browser symptom

The original browser reproduction was:

1. Sign in as Owner.
2. Open an Owner Community members route.
3. Sign out.
4. Sign in as Learner in the same browser context.
5. Observe the Learner return to the Owner route instead of `/app/home`.

## 2. Route root cause

Both sign-out controls called `clearSession()` while a protected Owner route was still active, then navigated to `/sign-in`. The auth-store update let `ProtectedRoute` preserve the current Owner pathname as `state.from`. `SignInPage` later consumed that state for the Learner.

The test-first reproduction confirmed the leaked state exactly:

```json
{"from":"/app/servers/owner-server/community/members"}
```

The guard now records whether its mounted tree started authenticated. A direct unauthenticated deep link still receives a return destination; an authenticated tree losing its session does not.

## 3. Query root cause

`AuthenticatedQueryCacheBoundary` cleared the QueryClient reactively in an effect after the user ID changed. Explicit sign-out did not cancel or clear queries before publishing the empty auth state, leaving a timing window in which the next account could mount against the prior account's cache or requests.

The red test placed private Owner data in the cache, started an abortable Home request, and subscribed to the auth store. At the moment `user` became null, the cache still contained the Owner data.

## 4. Atomic sign-out fix

`useSignOut` is now the single explicit sign-out operation for both frontend shells:

1. Capture the refresh token.
2. Cancel all active TanStack Query requests.
3. Clear query and mutation caches.
4. Clear local auth state.
5. Replace navigation with `/sign-in` and null route state.
6. Attempt server refresh-token revocation without weakening the local boundary on failure.

The green test observed the request abort and missing cache entry before the auth-store transition.

The API refresh adapter also verifies that the refresh token is still current before applying a completed refresh response. A refresh started by the previous account therefore cannot restore that account after sign-out.

## 5. Auth locator root cause

The sign-in/register mode controls were ordinary buttons inside a `tablist`, and the submit button used the same name as the sign-in mode selector. The reveal button also lived inside the password label, causing the input's computed accessible name to include `Show password`.

The corrected markup provides tabs plus a tab panel and makes the reveal button a sibling of the labelled password input. Playwright can now use exact role/label locators with no CSS workaround.

## 6. Real browser proof

The product regression created a temporary Owner-only Study Server, navigated the Owner to its members route, then performed sign-out and Learner sign-in in one browser context. It asserted:

- sign-out history state was null;
- Learner landed on `/app/home`;
- Learner Home returned 200;
- no API path after sign-out contained the Owner-only Study Server ID;
- the temporary server was removed afterward.

The focused regression passed, followed by all six signed-in product-critical journeys in system Chrome.

## 7. Final result

- No stale protected route survives explicit sign-out.
- Query data and active requests are detached before local session clear.
- Both v2 and legacy shells share the same sign-out boundary.
- Authentication controls are uniquely discoverable by assistive technology and Playwright.
- Owner-to-Learner switching passes without cross-account route or Study Server requests.
