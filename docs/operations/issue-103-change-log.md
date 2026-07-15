# Issue #103 — Playwright product-critical paths and no-dead-controls

## Summary

- Playwright config + `@critical` public smoke (CI) and `@product` signed-in paths (local stack)
- `make product-e2e-critical` / `make product-e2e`
- `docs/operations/no-dead-controls-inventory.md` + `scripts/check-no-dead-controls.mjs`
- CI: no-dead-controls gate + Playwright `@critical` against Vite preview

### Commands

```bash
make product-e2e-critical   # inventory gate + @critical (preview server)
make product-e2e            # product-up → seed → @product → product-down
```

### Coverage notes

`@product` covers sign-in (owner/learner), home, inbox, calendar, teaching, billing, friends. Broader chat/resources/office-hours/community paths remain smoke-expandable; inventory documents intentionally disabled controls (#100/#102/#104).
