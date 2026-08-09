# Issue #238 debug log: Product-readiness audit

**Date:** 2026-08-09
**Scope:** local test harness, signed-in browser behavior, authorization consistency, and public hosting state.

## 1. Local health false negative under sandbox isolation

### Symptom

An unprivileged `make product-health` could reach Vite on 5173 but reported connection failures on gateway/realtime/LiveKit ports.

### Investigation

Running the supervisor with host permissions reported the Docker containers and Java services already running. The subsequent host-permission `make product-health` passed every endpoint.

### Conclusion

This was sandbox port/socket visibility, not proven service downtime. Future agents should rerun local Docker/host-network health checks with the approved host permission before classifying an outage.

## 2. Signed-in Playwright could not identify Password

### Symptom

All existing `@product` tests failed at:

```ts
page.getByLabel('Password')
```

The locator matched both the input and the reveal button.

### Root cause

`SignInPage.tsx` nests the reveal button inside the same `<label>` as the input:

```tsx
<label>Password
  <span className="password-field">
    <input ... />
    <button aria-label="Show password">...</button>
  </span>
</label>
```

The computed input name becomes `Password Show password`. The mode selector and submit action also both expose a button named `Sign in`.

### Follow-up

[#240](https://github.com/Vinosaamaa/chanter/issues/240) owns semantic markup and the session-boundary regression test. [#241](https://github.com/Vinosaamaa/chanter/issues/241) owns dependable CI product E2E.

## 3. Playwright test runner instability

### Symptom

After working around ambiguous locators in a temporary test, the managed-machine Playwright test runner did not finish cleanly and required interruption. Trace artifacts showed it remained on sign-in.

### Isolation

A direct `playwright-core` script using system Chrome launched, authenticated, captured a screenshot, and exited in under five seconds. The direct audit then traversed desktop/mobile routes successfully.

### Conclusion

The system Chrome/app path works. The repository runner/browser provisioning and semantic locators are not a trustworthy release gate; #241 must make browser installation/execution deterministic and retain actionable traces.

## 4. Invited member and learner receive navigation/Home 403s

### Reproduction

1. Seed demo data with `make product-demo-seed`.
2. Sign in as `dev-demo-learner@chanter.local` in a fresh browser context.
3. Load `/app/home` and observe API responses.

Observed examples:

```text
403 GET /api/v1/study-servers/<id>/navigation
403 GET /api/v1/me/home-summary
```

The same failures occurred in a fresh context, ruling out only cross-account cache contamination.

### Root cause

`JdbcCourseRepository.listAccessibleStudyServers` includes `STUDY_SERVER_MEMBER`, while `JdbcCourseRepository.findViewerScope` returns empty unless the user is Owner, Instructor, TA, or enrolled Learner. `StudyServerNavigationService.findNavigation` requires that AI viewer scope before it builds otherwise server-wide navigation. `HomeSummaryService` loops the accessible list and calls the same failing navigation method.

This violates one authorization invariant: if the server list says a user can enter a Study Server, server-wide navigation/Home must not reject that same membership. Course/Cohort content still needs role/Enrollment filtering.

### Follow-up

[#239](https://github.com/Vinosaamaa/chanter/issues/239) owns the TDD fix and role matrix.

## 5. Cross-account previous-route leak

### Reproduction

1. Sign in as Owner.
2. Open a Community members route.
3. Sign out.
4. Sign in as Learner in the same browser context.
5. Observe the post-login route.

Observed: the Learner was sent to the Owner's previous Community members route instead of `/app/home`.

### Root cause

`V2Sidebar.signOut` calls `clearSession()` while still on the protected route, then explicitly navigates to `/sign-in`. The auth-state change lets `ProtectedRoute` render first and preserve the current route as `state.from`. That stale redirect is then consumed by the next login. Active/cached queries can also race the identity transition.

### Follow-up

[#240](https://github.com/Vinosaamaa/chanter/issues/240) owns cancellation/cache clearing, deterministic sign-out navigation, and the owner-to-learner browser regression.

## 6. Visible unavailable controls

The route audit recorded visible disabled controls in Billing, Friends/DM, Course Chat/Questions, Community Lounge, and Community Members. Some submit controls are correctly disabled until input exists, but production placeholders such as video/file/emoji and disabled settings tabs violate the stated no-dead-control goal.

[#254](https://github.com/Vinosaamaa/chanter/issues/254) owns a route-by-route inventory and implement-or-remove gate after capability issues land.

## 7. Public hostname and Cloudflare

Verified:

- `chanter.app` serves GoDaddy parking.
- Nameservers are GoDaddy, not Cloudflare.
- No Wrangler/Cloudflare config exists in the repository.

Not verified:

- Cloudflare account products, traffic, or billing. Browser/account authentication was unavailable, and the `gh` token also lacks project scopes (unrelated but recorded for operations).

Conclusion: repository/public Cloudflare usage is zero; account-level usage is unknown.
