# Chanter frontend

React + TypeScript + Vite client for the Chanter education platform.

## New here?

**Start the full stack and try the app:** [`docs/operations/getting-started.md`](../docs/operations/getting-started.md)

## What this is today

**Legacy production shell** (merged #47–#59): auth, Study Server navigation via **channel tree**, live channel chat, voice, Friends/DM, search, instructor dashboard, and onboarding routes.

**Target UI (active build):** **Course-first shell** — epic [#115](https://github.com/Vinosaamaa/chanter/issues/115), start [#116](https://github.com/Vinosaamaa/chanter/issues/116). Implement to match [`docs/product-design/DESIGN-DECISIONS.md`](../docs/product-design/DESIGN-DECISIONS.md) and PNGs in `docs/product-design/mockups/learner-flow/` and `mockups/owner-flow/`.

The API demo harness at **`/dev/demo`** is for developers testing APIs — **not** the main product UI.

## Target routes (v2 — implement in #116+)

| Path | What you do there |
|------|-------------------|
| `/` | Marketing landing page |
| `/sign-in` | Sign in or create account |
| `/app/home` | Home — multi-course dashboard |
| `/app/teaching` | Teaching ops dashboard (role-gated) |
| `/app/inbox` | Unified inbox |
| `/app/calendar` | Cross-course calendar |
| `/app/friends` | Friends list + live DM (+ optional voice) |
| `/app/servers/:id/community/:tab` | Hub tabs: announcements, lounge, events, discover, members |
| `/app/servers/:id/courses/:courseId/:tab` | Course workspace: overview, chat, questions, resources, office-hours, people |
| `/app/onboarding/create-study-server` | Create Study Server wizard |

Legacy routes (`/app/servers/:id/study-channels/...`, `/app/instructor-dashboard`, etc.) remain until v2 slices migrate them.

## Stack

- React Router, TanStack Query, Zustand
- Tailwind CSS v4 (`@tailwindcss/vite`)
- Shared UI in `src/components/ui/`
- API via `src/lib/api-client.ts` (Vite proxy to gateway `:8080`)

## Local development

**Recommended** — run the whole product (backend + frontend):

```bash
# from repo root
make product-up
```

Frontend only (needs backend already running):

```bash
make frontend-install
make frontend-dev   # http://localhost:5173
```

## Target product UI

| Doc | Purpose |
|-----|---------|
| [`DESIGN-DECISIONS.md`](../docs/product-design/DESIGN-DECISIONS.md) | Canonical v2 decisions — **read first** |
| [`specs/layout-rules.md`](../docs/product-design/specs/layout-rules.md) | Chrome, active states, browser framing |
| [`ui-v2-issue-breakdown.md`](../docs/issues/ui-v2-issue-breakdown.md) | Implementation slices #116–#128 |
| [`product-design/README.md`](../docs/product-design/README.md) | Mockup gallery index |

Cursor rule: [`.cursor/rules/ui-v2.mdc`](../.cursor/rules/ui-v2.mdc) (applies when editing `frontend/**`).
