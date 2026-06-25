# Issue #48 Change Log

Issue: [#48 Bootstrap Production Frontend Foundation](https://github.com/Vinosaamaa/chanter/issues/48)

## Summary

Established the production frontend architecture: React Router, TanStack Query, Zustand, Tailwind v4 design tokens (dark theme, indigo accents), feature-based folders, shared UI primitives, and a gateway API client. Moved the legacy vertical-slice harness to `/dev/demo`.

## Structure

```
frontend/src/
  app/                 Router + providers
  components/ui/       Button, Card primitives
  features/
    auth/pages/        Sign-in placeholder (#49)
    marketing/pages/   Public landing placeholder
    shell/             App shell layout + placeholder (#50)
    dev-demo/          Legacy API demo (was App.tsx)
  lib/                 api-client, cn()
  stores/              Zustand app store placeholder
```

## Routes

| Path | Purpose |
|------|---------|
| `/` | Public landing |
| `/sign-in` | Auth placeholder |
| `/app` | Authenticated shell layout + placeholder |
| `/dev/demo` | Legacy API demo harness |

## Dependencies added

- `react-router-dom`, `@tanstack/react-query`, `zustand`, `clsx`, `tailwind-merge`
- `tailwindcss`, `@tailwindcss/vite` (dev)

## Verification

```bash
cd frontend && npm run lint && npm run build
```

Manual: `npm run dev` → visit `/`, `/sign-in`, `/app`, `/dev/demo`.
