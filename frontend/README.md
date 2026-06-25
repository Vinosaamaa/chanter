# Chanter frontend

React + TypeScript + Vite client for the Chanter education platform.

## What this is today

Production shell foundation (**#48**): React Router, TanStack Query, Zustand, Tailwind design tokens, and feature-based routes under `src/app/` and `src/features/`.

The legacy vertical-slice API demo lives at **`/dev/demo`** (`src/features/dev-demo/DevDemoApp.tsx`). It is **not** the production product shell.

**Active work:** [Production Frontend](https://github.com/users/Vinosaamaa/projects/3) — next slice **#49** after **#48** merges. Workflow: [`docs/operations/agent-workflow.md`](../docs/operations/agent-workflow.md).

## Routes

| Path | Purpose |
|------|---------|
| `/` | Public landing placeholder |
| `/sign-in` | Auth placeholder (#49) |
| `/app` | App shell layout placeholder (#50) |
| `/dev/demo` | Legacy API demo harness |

## Stack

- React Router, TanStack Query, Zustand
- Tailwind CSS v4 (`@tailwindcss/vite`)
- Shared UI primitives in `src/components/ui/`
- API client: `src/lib/api-client.ts` (gateway via Vite proxy)

## Target product UI

**[`docs/product-design/README.md`](../docs/product-design/README.md)** — 19 mockups, visibility model, user journeys.

## Local development

```bash
make frontend-install
make frontend-dev   # http://localhost:5173 — proxies /api and /actuator to gateway :8080
```

## Agent pointers

- **Glossary:** [`CONTEXT.md`](../CONTEXT.md)
- **Handoff:** [`HANDOFF.md`](../HANDOFF.md)
- **Workflow + issue order:** [`docs/operations/agent-workflow.md`](../docs/operations/agent-workflow.md)
