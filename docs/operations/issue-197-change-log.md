# Issue #197 Change Log — SEC-16: Constant-time login for unknown users

## Problem

`AuthSessionService.login` returned early when the email was unknown, skipping `passwordEncoder.matches` / bcrypt. That created a timing side-channel for account enumeration.

## Changes

- Always call `passwordEncoder.matches` against either the user's hash or a precomputed dummy BCrypt hash.
- Unit test asserts unknown emails still invoke `matches` with the dummy hash.

## Acceptance

- [x] Unknown-user login still runs bcrypt-style matches
- [ ] CI green + CodeAnt
- [ ] Browser: demo owner sign-in still works
