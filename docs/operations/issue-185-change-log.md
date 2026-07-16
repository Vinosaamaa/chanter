# Issue #185 change log — Gate `/dev/demo` out of production builds (SEC-10)

**Branch:** `cursor/185-gate-dev-demo-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-10

## Summary

The legacy `/dev/demo` API harness (including the hardcoded `chanter-dev-demo` password module) is registered only when `import.meta.env.DEV` is true, via a React Router `lazy` import. Vite eliminates that branch from production builds. Marketing “View demo” and shell placeholder links are similarly DEV-gated. `npm run build` runs `scripts/check-dev-demo-prod-bundle.mjs` to fail CI if the harness leaks into `dist/`.

Local product demo accounts from `make product-demo-seed` are unchanged; use `/sign-in` with those credentials. `/dev/demo` remains available under `npm run dev`.

## Changes

| Area | Change |
|------|--------|
| `router.tsx` | DEV-only lazy `/dev/demo` route; no static `dev-demo` imports |
| `DevDemoLazyRoute.tsx` | Lazy entry for the harness |
| `MarketingHeader` / `AppShellPlaceholderPage` | Demo links only in DEV |
| `marketing-routes.ts` | `isMarketingDemoEnabled()` helper |
| `check-dev-demo-prod-bundle.mjs` | Post-build assert no password/harness strings in `dist/` |
| `getting-started.md` | Document DEV-only playground |

## Tests

```bash
cd frontend && npm test -- --run src/features/marketing/marketing-routes.test.ts src/features/marketing/pages/LandingPage.test.tsx
cd frontend && npm run build   # includes SEC-10 dist check
```
