# Issue #196 Change Log — SEC-15: Neutralize register account enumeration

## Problem

`POST /api/v1/auth/register` returned `409 Email is already registered` when the email existed, allowing account enumeration (unlike forgot-password, which always returns a neutral success).

## Changes

- Existing email (and unique-constraint race): return `202` with the same neutral body as email-verification signup (`verificationRequired` + generic message) — never `409`.
- Out-of-band email to the existing account via `ProductionAuthService.notifyExistingAccountRegisterAttempt`.
- New/updated smoke tests for duplicate register under verification on/off.

## Acceptance

- [x] Duplicate register does not return 409 / "already registered"
- [x] Out-of-band notice emailed to existing account
- [ ] CI green + CodeAnt
- [ ] Browser: register / sign-in still works
