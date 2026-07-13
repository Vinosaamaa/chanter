# Issue #117 - change log

**Branch:** `feature/116-v2-app-shell`
**Mockups:** `journey-1-signup-invite.png`, `journey-2-welcome-joined.png`, `owner-o1-create-study-server-wizard.png`

## Summary

- Rebuilt sign-in and registration with the v2 split-screen invite layout while preserving the existing auth API calls.
- Added the post-enrollment welcome overlay and first-course actions.
- Replaced the full-page Study Server setup with a four-step modal over Home, backed by the existing create API.
- Corrected v2 search focus from `Command-K` to the canonical `Command-F` and removed the retired global search overlay from the v2 shell.

## Verification

```bash
cd frontend
npm run test -- src/features/v2-shell/components/V2TopBar.test.tsx
npm run lint
npm run build
```

All commands passed. Browser comparison is included in the final #117-#128 responsive visual pass.
