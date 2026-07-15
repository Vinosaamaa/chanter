# Issue #104 — Public beta launch checklist and landing polish

## Summary

- `docs/operations/public-beta-launch-checklist.md` — security, backup, monitoring, legal, product readiness, sign-off
- Landing `/` hero uses inline product chrome (`MarketingProductPreview`): course stats, TA queue, Join Queue CTA
- `/privacy` placeholder page + footer Terms / Privacy / support@chanter.example
- README + HANDOFF point at staging URL placeholder and beta scope; course storefront commerce marked post-MVP

## Known limitations (beta)

- `course-storefront.png` commerce storefront — post-MVP
- Marketing header friends/notification badges — post-launch polish

## Verify

```bash
cd frontend && npm test -- --run src/features/marketing/pages/LandingPage.test.tsx
# Manual: open / → product preview + Join Queue; /privacy loads
```
