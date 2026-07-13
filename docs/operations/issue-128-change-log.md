# Issue #128 - change log

**Branch:** `feature/116-v2-app-shell`
**Visual reference:** legacy `landing-page.png`, refreshed to the approved v2 course-first brand

## Summary

- Rebuilt the public landing page around a full-bleed capture of the approved v2 Home workspace.
- Reused the v2 Chanter brand and introduced a responsive desktop/mobile marketing header.
- Replaced emoji and generic component-library visuals with Lucide icon swatches and purpose-built feature, use-case, pricing, and footer bands.
- Preserved visitor and authenticated Create Study Server routing, sign-in, demo, Docs, and section-anchor behavior.
- Added responsive constraints for phone, 720p, 1080p, and 4K layouts without horizontal overflow.

## Verification

```bash
cd frontend
npm run test -- src/features/marketing/pages/LandingPage.test.tsx src/features/marketing/marketing-routes.test.ts
npm run lint
npm run build
```

- Browser: landing verified at 390x844, 1280x720, 1920x1080, and 3840x2160.
- Browser: mobile menu, feature/use-case/pricing anchors, visitor CTAs, and signed-in shell routes verified.
- Browser: all #117-#127 routes returned their expected path and headings at 1280x720 with no document overflow or console errors.
- Full-stack: clean `make product-supervise` plus `make product-health` passed before owner-only create flows were checked.

The owner explicitly waived TDD for this visual PR. Existing behavior tests were retained and the full frontend suite remains the final gate.
