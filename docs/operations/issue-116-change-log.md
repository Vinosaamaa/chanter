# Issue #116 — change log

**Branch:** `feature/116-v2-app-shell`
**Mockup target:** `docs/product-design/mockups/learner-flow/journey-3-home.png`

## Summary

Introduced a new **v2 app shell** (`frontend/src/features/v2-shell/`) instead of retrofitting the legacy Discord-style layout. The signed-in default route is now `/app/home`.

## What shipped

- **V2AppShellLayout** — full-height sidebar + top bar over right pane only
- **V2Sidebar** — Home, Teaching (role-gated), Inbox, Calendar, Friends, server course groups, + Join or create, profile row flush bottom
- **V2TopBar** — breadcrumb, centered search (⌘F), single notification bell
- **HomePage** — greeting, needs-attention row, Continue learning 2×2 grid, Up next panel
- **v2 route stubs** — inbox, calendar, teaching, course workspace, community hub
- **Legacy routes** — channel tree + instructor tools remain on `AppShellLayout` under `/app/servers/...`

## Design system (Chativity MockUp UI — source of truth)

Replaced custom token/primitive layer with a **scoped copy** of Chativity `MockUp UI/app/globals.css`:

- **`v2-mockup.css`** — full responsive mockup stylesheet (`.v2-app-shell` scope)
- **`lucide-react`** — same icons as reference `page.tsx`
- **Components** — markup/class names match mockup (`notice-row`, `course-card`, `timeline`, etc.)
- **Spec:** `docs/product-design/specs/v2-design-tokens.md` (points to MockUp UI)

## Tests

```bash
cd frontend && npm run test && npm run lint && npm run build
```

- `v2-routes.test.ts` — route + search scope helpers
- `build-home-view-model.test.ts` — home view model
- `HomePage.test.tsx` — greeting + sections render

## Browser verify

1. `make product-up` (or `make product-supervise`)
2. Sign in as demo user → lands on `/app/home`
3. Confirm sidebar chrome, top bar, and home sections match `journey-3-home.png`
