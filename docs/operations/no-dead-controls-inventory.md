# No-dead-controls inventory (#103)

Primary controls that are intentionally disabled must be listed here with an owning issue.
Enabled primary controls are exercised by Playwright (`@critical` / `@product`) or Vitest page tests.

| Control | Location | State | Owning issue / note |
|---------|----------|-------|---------------------|
| Continue with Google | `/sign-in` | Disabled until OAuth env configured | #102 — enables when `CHANTER_OAUTH_GOOGLE_*` set |
| Marketing friends/notification badges | Landing header | Not shipped | #104 non-goal / post-launch |
| Course storefront commerce CTA | Landing / marketing | Out of MVP | Post-MVP; known limitation in public beta checklist |
| Ask AI while streaming | Course Questions | Temporarily disabled during SSE | #100 — prevents double submit |
| Mark helpful after already marked | Course Questions | Disabled once `helpfulMarked` | #100 |

## Gate

`node scripts/check-no-dead-controls.mjs` fails if a `disabled` primary control title/comment references an unknown issue pattern without appearing in this inventory (heuristic).

Manual review: when adding `disabled` to a primary CTA, add a row above.
