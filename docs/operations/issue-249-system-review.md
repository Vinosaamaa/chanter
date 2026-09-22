# Issue #249 system review

## Implemented boundary

Auth owns separate ADMIN/REVIEWER grants, session-bound second-factor verification, assigned cases, preserved evidence, reversible timed restrictions, verified-email appeals and append-only audit records. Product roles grant no operator authority. Evidence requires current source permission when reported and assigned reviewer or administrator authority when investigated. Privileged reads record an investigation reason. The browser clears evidence on verification expiry and rejects late responses from the former verification.

Restriction changes, audit records and durable email notices commit together. USER suspension revokes browser sessions, refresh credentials and operator verification. Session issuance checks moderation while holding the same user-row lock as suspension. The concurrent provider-issuance regression proves that suspension and later reinstatement cannot revive an intervening session. Refresh rotation and current-account/profile checks are atomic; unexpected failures roll back rotation, while replay commits family revocation before returning 401.

Introspection is an uncached point-in-time read. An overlapping suspension can commit while a previously valid response travels to its caller. This creates no persistent session, and native release/acceptance recheck current auth authority. No cross-service transaction or instantaneous revocation guarantee is claimed.

## Content, realtime and media

Seven servlet services check current account status. Community resolves public course/cohort/channel/office-hours routes to the owning Study Server and filters restricted navigation. Media checks resource/server authority before and after public and ingestion reads. Quarantine also denies public resource deletion, preserving evidence. Search projections cannot confer access through stale index entries.

Message-service serializes block/unblock, friend acceptance, message writes, history reads and call eligibility on the canonical account pair. Real concurrent database tests cover the observed block/write/read/call races. Source IDs are checked in batches of at most 100. Existing whole-history queries and retained pair-lock rows remain explicit launch-scale limits; this change does not claim database pagination.

Realtime checks current account, session, pair and channel authority before delivery. Confirmed denial removes access; an authority outage propagates without discarding a valid channel subscription. Periodic revalidation closes suspended sessions and clears stale presence. Remote calls have bounded deadlines/concurrency; presence reconciliation caps work at 1,000 peers and four seconds. The five-second schedule is not an end-to-end revocation guarantee under load.

LiveKit join tokens pass a private current-room/account guard before signaling. Ordinary SDK tokens may omit optional `nbf`; a present claim remains validated. Exceptional release probes require the exact private health room, bounded lifetime and disabled publish/subscribe/data capabilities. Bounded server reconciliation removes existing participants. Authority failure denies access and unexpected reconciliation failures remain observable.

Production Caddy copies the query token into a temporary header, requires successful community authorization, removes the header, strips `/livekit`, and proxies unpublished port 7880. Actual adapted JSON verifies this order in release smoke. Only frontend/community join the dedicated internal authorization network; frontend retains its isolated edge identity. The accepted trace filtering, semantic model and native companion configuration are preserved. Epoch 9 prevents rollback to applications without these checks.

## Verification and remaining release evidence

The full frontend suite passes 325 tests, lint and the production build. Core JavaScript/CSS limits are unchanged; exact deferred moderation entries have separately approved caps. Retained fresh landing, sign-in and Home network measurements contain no moderation assets. Safety and Operator routes load the deferred entries, and their desktop/phone pixels and accessibility checks were inspected.

Hosted backend verification passes, including a real PostgreSQL test that rejects audit/note mutation and verifies retained audit data after reconnect. Both native AMD64/ARM64 packaged staging jobs passed at `ae64787d`, including migrations, auth startup, actual Caddy adaptation and the strictly limited signed health handshake. [Packaged staging evidence](https://github.com/Vinosaamaa/chanter/actions/runs/35774543312).

The combined PostgreSQL/Redis/Caddy/LiveKit/browser proof has established nonzero transmitted/received audio, sender removal after an operator action, unchanged bytes at a still-connected receiver, WebSocket close 1008, HTTP/media denial and 403 on reconnect with the same unexpired token. The [complete hosted journey](https://github.com/Vinosaamaa/chanter/actions/runs/35776054234) passed at `23be93a8`: the real issued refresh credential returned 401, a delivered verified-email appeal was saved and reversed through the operator interface, and a fresh login succeeded. All new appeal/operator screenshots were inspected at 390 and 1280 pixels with no overlap or horizontal overflow; accessibility checks passed. The fixture additionally attempts a bounded real WebSocket upgrade alongside the explicit signaling 403. Final-head checks remain the PR acceptance gate. No local preview stack or browser attachment was started for this proof.

Full CodeAnt reviews and their correctness fixes are recorded in the review disposition. Remaining performance suggestions retain bounded current authorization rather than introduce positive caches or a new policy/reconciliation framework.

Production operator key provisioning, real administrator enrollment and production exercise remain separate evidence. The optional distinct encryption key enables operator verification without making ordinary startup depend on enrollment. Database superusers who can remove audit triggers remain outside the application's immutability guarantee. Account-lifecycle retention/deletion is coordinated with #251. Issue #249 stays open through merged-main and release/operator verification.
