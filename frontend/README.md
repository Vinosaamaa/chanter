# Chanter frontend

React + TypeScript + Vite client for the Chanter education platform.

## What this is today

`src/App.tsx` is a **vertical-slice API demo** — forms and buttons that exercise backend endpoints. It is **not** the production product shell.

**Active work:** [Production Frontend](https://github.com/users/Vinosaamaa/projects/3) — start at **[issue #48](https://github.com/Vinosaamaa/chanter/issues/48)**. Agent order: [`docs/operations/agent-workflow.md`](../docs/operations/agent-workflow.md).

## Target product UI

The finished **browser web app** UX — Discord-like Study Server layout, Friends hub, instructor dashboard, AI citations, etc. — is documented with **19 concept mockups** here:

**[`docs/product-design/README.md`](../docs/product-design/README.md)**

**Visibility (global friends + my-courses sidebar):** [`visibility-and-social-model.md`](../docs/product-design/visibility-and-social-model.md)

| Mockup | Slice |
|---|---|
| [`app-shell.png`](../docs/product-design/mockups/app-shell.png) | Core channel layout |
| [`friends-hub-dm.png`](../docs/product-design/mockups/friends-hub-dm.png) | Friends + live DM (#31) |
| [`ai-support-question.png`](../docs/product-design/mockups/ai-support-question.png) | `#questions` + AI (#16–#19) |
| [`instructor-dashboard.png`](../docs/product-design/mockups/instructor-dashboard.png) | Instructor ops (#23) |

Full gallery: [`docs/product-design/mockups/README.md`](../docs/product-design/mockups/README.md)

## Planned stack (from `plan.md`)

- React Router, TanStack Query, Zustand
- WebSocket client via `realtime-service` (Milestone 3)
- Component library TBD (likely Tailwind / shadcn-style)
- OpenAPI-typed API client

## Local development

From repo root:

```bash
make frontend-install
make frontend-dev   # http://localhost:5173 — proxies /api to gateway :8080
```

Requires backend services running — see root [`README.md`](../README.md).

## Agent pointers

- **Glossary:** [`CONTEXT.md`](../CONTEXT.md)
- **Current slice / handoff:** [`HANDOFF.md`](../HANDOFF.md) and [`docs/operations/agent-workflow.md`](../docs/operations/agent-workflow.md)
- **Backend contracts:** service READMEs under `backend/`
