# Issue #102 — CodeAnt fix

**PR:** https://github.com/Vinosaamaa/chanter/pull/172  
**Branch:** `cursor/102-production-auth-6106`

## Round 1

- SAST HIGH: `LoggingEmailSender` logged sensitive email content → redacted body, then removed recipient/subject from logs.
- Frontend lint: setState-in-effect on verify/OAuth pages → derive missing-token errors outside effect.
- CI flake: `SocialRealtimeWebSocketSmokeTest` timeout → empty-commit re-push.

## Round 2

Awaiting re-check after log-provider silence + CI re-run.
