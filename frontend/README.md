# Chanter frontend

React + TypeScript + Vite client for the Chanter education platform.

## New here?

**Start the full stack and try the app:** [`docs/operations/getting-started.md`](../docs/operations/getting-started.md)

## What this is today

Production app shell with auth, Study Server navigation, live channel chat, voice, Friends/DM, search, instructor dashboard, and onboarding routes.

The legacy API demo harness at **`/dev/demo`** is for developers testing APIs — **not** the main product UI.

## Main routes

| Path | What you do there |
|------|-------------------|
| `/` | Marketing landing page |
| `/sign-in` | Sign in or create account |
| `/app` | App home (redirects to your Study Server) |
| `/app/servers/:id/home` | Study Server home — courses |
| `/app/servers/:id/study-channels/:channelId` | Study channels (`#general`, `study-room` voice, …) |
| `/app/servers/:id/course-channels/:channelId` | Course channels (`#announcements`, `#questions`, `#resources`) |
| `/app/friends` | Friends list + live DM (+ optional voice call) |
| `/app/instructor-dashboard` | Instructor metrics and operations |
| `/app/onboarding/create-study-server` | Create a new Study Server |
| `/dev/demo` | Legacy API demo (developers only) |

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

[`docs/product-design/README.md`](../docs/product-design/README.md) — mockups and vision.
