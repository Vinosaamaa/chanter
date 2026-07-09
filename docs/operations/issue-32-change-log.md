# Issue #32 — change log (in progress)

## Backend

- `realtime-service`: `DirectMessageCallHub` with invite/accept/decline/cancel/hang-up, ring timeout, busy detection, and structured call logs.
- WebSocket frames: `call_invite`, `call_accept`, `call_decline`, `call_cancel`, `call_end` → `call_ringing`, `call_accepted`, `call_busy`, `call_ended`.
- `POST /api/v1/direct-message-calls/{callId}/media-token` on realtime-service (active-call gate).
- `message-service`: `GET /api/v1/direct-message-calls/eligibility` reuses friendship + block rules.
- `community-service`: `LiveKitTokenIssuer.issueForDmCall` + internal media-token endpoint (`dm-call-{callId}` rooms).
- Gateway routes media-token POST to realtime-service; eligibility GET to message-service.

## Frontend

- Enabled Friends Hub **Call** button with overlay UX (ringing, accept/decline, mute, hang up).
- `social-realtime-client` call signaling; `use-friends-hub` wires LiveKit via existing `useLiveKitRoom`.

## Tests

- `DirectMessageCallSignalingSmokeTest` (invite → accept → media token; non-friend forbidden).
- `FriendRequestAndDirectMessageSmokeTest.directMessageCallEligibilityRequiresFriendshipAndRejectsBlocks`.

## Verification

- `mvn verify` (backend) — pass after stopping local product processes on 8080–8087.
- `npm run lint` + `npm run build` — pass.
- `DirectMessageCallSignalingSmokeTest`, DM call eligibility smoke — pass.
- Browser: Friends Hub loads for signed-in demo user; Call button + overlay present. Full two-tab call flow blocked by unstable local stack (`make product-up` docker realtime build fails; host-run stack intermittently drops gateway/realtime/frontend). Re-test during #63 E2E demo with stable `product-up`.

## After PR

- cubic fix loop (`docs/operations/issue-32-cubic-fix.md`).
- Two-browser LiveKit audio manual check (#63 precursor).
