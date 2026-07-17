# Issue #203 Change Log — SEC-22: Validate OAuth authorization URL scheme

## Problem

Sign-in rendered the server-provided Google OAuth `authorizationUrl` into an `href`
without checking the scheme, so a compromised/misconfigured API could inject
`javascript:` (or similar) URLs.

## Changes

- Added `isHttpOrHttpsUrl` helper — only `http:` / `https:` absolute URLs pass.
- `SignInPage` renders the Google button as a link only when the URL is safe;
  otherwise shows the disabled fallback.
- Unit tests cover accept/reject cases.

## Acceptance

- [x] Only http/https authorization URLs become `href`
- [x] Unit tests for scheme validation
- [ ] CI + CodeAnt
- [ ] Browser: sign-in page still loads (Google button disabled without OAuth config)

## Verification

```bash
cd frontend
npm test -- --run src/features/auth/is-http-or-https-url.test.ts src/features/auth/pages/SignInPage.test.tsx
npm run lint
```
