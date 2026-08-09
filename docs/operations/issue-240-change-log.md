# Issue #240 change log: Browser session isolation and auth accessibility

**Issue:** [#240](https://github.com/Vinosaamaa/chanter/issues/240)
**Branch:** `fix/240-browser-session-isolation`
**Date:** 2026-08-09

## Outcome

Made explicit sign-out an atomic account boundary. Chanter now cancels in-flight TanStack Query requests, removes cached query and mutation state, clears local authentication, and navigates to a clean sign-in entry before another account can mount. Owner routes and Study Server IDs no longer leak into a subsequent Learner session.

Authentication controls now expose distinct accessible semantics: mode selectors are tabs, the form is their tab panel, the password input is named exactly `Password`, the reveal control keeps its own name, and the submit action is the only button named `Sign in`.

## TDD evidence

### Auth semantics red to green

The first test could not find a `tab` named `Sign in`; both mode selectors were ordinary buttons. After the markup change, both auth modes expose `role="tab"`, `aria-selected`, `aria-controls`, roving `tabIndex`, Arrow/Home/End keyboard navigation, and one labelled tab panel.

### Stale route red to green

Ending a session from an Owner route initially produced:

```json
{"from":"/app/servers/owner-server/community/members"}
```

`ProtectedRoute` now distinguishes an unauthenticated deep link from a protected tree that started authenticated. Session loss from the latter redirects without `state.from`.

### Cache boundary red to green

The sign-out regression seeded an Owner cache entry and an abortable request, then observed the auth-store transition. Before the fix, the Owner cache was still present when the store published `user: null`.

The shared sign-out operation now enforces the order:

```ts
await queryClient.cancelQueries()
queryClient.clear()
useAuthStore.getState().clearSession()
navigate('/sign-in', { replace: true, state: null })
```

Refresh-token revocation remains best-effort after the local boundary, so a network outage cannot keep private browser state attached.

The API refresh adapter also verifies that the refresh token is still current before applying a completed refresh response, so an old account cannot be restored after sign-out.

## Code changes

- Added `useSignOut` and routed both `V2Sidebar` and the legacy `AppTopBar` through it.
- Discarded stale refresh responses when the refresh token changes during an account transition.
- Updated `ProtectedRoute` to omit a return destination when its authenticated tree loses the session.
- Gave auth modes true tab/tabpanel semantics and separated the password label from its reveal button.
- Replaced CSS-selector auth workarounds in product Playwright tests with label/role locators.
- Added an Owner-to-Learner browser regression that creates an Owner-only Study Server, signs out, signs in as Learner, and rejects any post-boundary request containing that server ID.

Representative browser assertion:

```ts
await page.getByRole('menuitem', { name: 'Sign out' }).click()
await expect(page).toHaveURL(/\/sign-in$/)
expect(await page.evaluate(() => window.history.state?.usr ?? null)).toBeNull()

await submitCredentials(page, learnerEmail)
await expect(page).toHaveURL(/\/app\/home$/)
expect(apiPathsAfterSignOut.some((path) => path.includes(ownerOnlyServer.id))).toBe(false)
```

## Verification performed

```text
Targeted auth/session tests                   PASS (4 files / 8 tests)
npm run lint                                  PASS
npm run test                                  PASS (67 files / 217 tests)
npm run build                                 PASS
make product-health                           PASS
make product-demo-seed                        PASS
Owner -> sign-out -> Learner browser test     PASS
Full signed-in product-critical browser suite PASS (6 tests)
```

The known production bundle-size and ineffective dynamic-import warnings remain assigned to #241/#254. Deterministic managed-browser provisioning remains assigned to #241; local product verification used installed system Chrome.
