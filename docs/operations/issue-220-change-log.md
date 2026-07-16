# Issue #220 Change Log — Hotfix: realtime subscribe internal token (SEC-01 regression)

## Problem

After #188, community public APIs require a Bearer JWT **or** `X-Chanter-Internal-Service-Token` + `X-User-Id`.  
`HttpChannelSubscriptionAuthorizer` still called community with only `X-User-Id`, so WebSocket channel subscribe failed with:

`Community Service rejected channel access: ... 401 Unauthorized .../channel-message-access`

Friends/DM HTTP clients were updated in #188; this authorizer was missed. Browser auth signed in successfully, but course channel chat could not load.

## Changes

- `HttpChannelSubscriptionAuthorizer` now sends `AuthHeaders.INTERNAL_SERVICE_TOKEN` with `X-User-Id` (same pattern as `HttpChannelMessageClient`).
- Added `HttpChannelSubscriptionAuthorizerTest` (JDK `HttpServer`) asserting both headers on subscribe access.

## Acceptance

- [x] Unit coverage that the authorizer sends the internal token
- [ ] Realtime subscribe works for demo owner/learner (browser after deploy)
- [ ] Browser: announcements channel message send
