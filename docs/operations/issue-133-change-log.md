# Issue #133 - change log

**Branch:** `feature/133-operational-v2-shell-flows`
**Commit:** `feat(ui-v2): #133 operationalize shell search and account flows`

## Goal

Make the UI v2 shell's global search, account, sign-out, join/create, and public auth destinations truthful and operational. Remove placeholder unread signals until durable notification data ships in #143.

## What changed

### Route-scoped global search

- Connected the v2 top-bar search field and `Command-F`/`Control-F` shortcut to the existing global search provider.
- Forced active Course routes to search only that accessible Course.
- Filtered every backend hit against the navigation response so inaccessible Course results never render.
- Deep-linked Resources and approved FAQs into the v2 Course workspace instead of legacy support routes.
- Kept `Command-K` and `/` as backwards-compatible shortcuts for the legacy shell.

```tsx
const activeCourseFilter = variant === 'v2' && routeCourseId
  ? routeCourseId
  : courseFilter !== 'all' && !courseIds.has(courseFilter) ? 'all' : courseFilter

const base = results.filter((hit) => courseIds.has(hit.courseId))
```

### Account and sign-out

- Replaced the profile row's unconditional Billing link with an accessible account menu.
- Shows Billing only when the backend-issued Study Server capability permits it.
- Revokes the refresh token when possible, always clears the local session, and returns to `/sign-in` even if revocation is temporarily unavailable.
- Replaced the fake notification badge with a real Inbox destination.

```tsx
try {
  await logoutApi(refreshToken)
} catch {
  // A local sign-out must still complete if token revocation is unavailable.
}
clearSession()
navigate('/sign-in', { replace: true })
```

### Join or create

- Added `/app/onboarding/join-or-create` as the shell chooser destination.
- Accepts either invite query values or a complete copied invite URL.
- Uses the existing durable Cohort join API, invalidates Study Server/navigation queries, and returns to the refreshed Home shell.
- Links owners to the existing Create Study Server wizard.

```tsx
await joinCohort(invite.cohortId, invite.inviteCode)
await Promise.all([
  queryClient.invalidateQueries({ queryKey: ['study-servers'] }),
  queryClient.invalidateQueries({ queryKey: ['study-server-navigation'] }),
])
navigate('/app/home', { replace: true })
```

### Truthful public and shell states

- Added a real public `/terms` route and Terms of Service page.
- Presents unavailable Google sign-in as an explicitly disabled, described control.
- Removed synthetic Inbox, Course unread, Home message/resource, announcement, and auth-preview counts.
- Tightened Home Course cards after the count row was removed so the responsive layout does not leave dead space.

## TDD coverage

Red/green cycles cover:

- `Command-F` opening global search;
- accessible Course filtering and v2 result destinations;
- top-bar/provider integration;
- account-menu sign-out and local session clearing;
- full copied invite URL parsing and durable join query refresh;
- absence of synthetic unread and Home summary counts;
- real Terms destination and disabled Google provider state;
- mobile sidebar open/close behavior;
- canonical join-or-create route generation.

## Browser verification

Verified against the full local product stack on 2026-07-13:

- Course `Command-F` opened the real search overlay with the routed Course selected and inaccessible Course choices absent.
- A real search request produced the correct empty state without browser errors; v2 result deep-link behavior is covered by the component boundary test.
- Join or create opened from the sidebar and malformed invite text produced a clear inline alert.
- At `390x844`, the content remained 390px wide with no horizontal overflow; the 335px sidebar was fully off-canvas when closed and opened/closed from the top bar.
- Owner account menu exposed capability-gated Billing; Sign out cleared the session and navigated to `/sign-in`.
- The disabled Google provider and real `/terms` page rendered correctly.
- The demo owner signed back in and returned to `/app/home`.
- Browser console contained zero warning or error entries.

The first browser pass exposed an event-order bug that unit-level click dispatch did not reproduce: opening search on input focus mounted the overlay before pointer-up, allowing the same click to land on the backdrop and close it. Removing the focus trigger kept explicit click and keyboard entry stable; a shell integration test now asserts that the dialog remains open after clicking the top-bar search control.

## Verification

```bash
cd frontend
npm run lint
npm run test
npm run build

cd ..
make product-health
```

Final results:

- frontend: 27 test files and 85 tests passed;
- frontend lint passed;
- frontend production build passed with the existing non-blocking Vite chunk-size warning;
- `git diff --check` passed;
- gateway, auth, realtime, and LiveKit health checks passed.

## Architecture note

No backend service boundary, persistence model, or cross-service contract changed in #133, so `System Design.md` does not require an update. The slice reuses the existing auth, search, onboarding, navigation, and Study Server APIs.
