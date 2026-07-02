# Issue #59 — Public Marketing Landing Page

## Summary

Replaced the #48 landing placeholder with a mockup-aligned marketing page: hero, app preview mock, feature bands, use cases, pricing teaser, and footer. No auth required.

## Routes

| Path | Purpose |
|------|---------|
| `/` | Public marketing landing (visitors and signed-in users) |

## CTAs

| Control | Destination |
|---------|-------------|
| Create Study Server / Get started | `/app/onboarding/create-study-server` when signed in; otherwise `/sign-in` with redirect to onboarding |
| View demo | `/dev/demo` |
| Sign in | `/sign-in` |

## Module

`frontend/src/features/marketing/`

- `marketing-routes.ts` — CTA path helpers
- `marketing-content.ts` — feature / pricing copy
- `components/MarketingHeader.tsx`, `AppPreviewMock.tsx`
- `pages/LandingPage.tsx`

## TDD

- `marketing-routes.test.ts`
- `pages/LandingPage.test.tsx`

## Browser test (manual)

1. Visit `/` while signed out → hero, features, pricing visible; Create Study Server → sign-in.
2. Sign in → revisit `/` → Create Study Server → onboarding wizard.
3. View demo → `/dev/demo`.
4. Resize to mobile width → header CTAs remain usable; sections stack.

## Deferred

- Animated product screenshots / video hero
- Dedicated pricing page and docs site (header Docs links to GitHub for now)
