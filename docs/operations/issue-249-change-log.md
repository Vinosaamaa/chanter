# Issue #249 change log

Scope is recorded on #249 and implemented in PR #333. The branch incorporates accepted gateway, durable resource ingestion, native session and semantic retrieval work through epoch 8. Moderation adds epoch 9. Auth V5-V7 and message V11 precede the separately reserved account-lifecycle migrations.

Auth now owns separately granted platform roles, encrypted authenticator enrollment, replay-protected session verification, assigned cases, bounded evidence, reversible restrictions and append-only audit records. Reporter context links preserve exact saved source references. Safety, appeal and operator routes have explicit loading/failure/saved states and retain unconfirmed drafts. The operator interface is separately addressed; ordinary product navigation confers no authority.

Restrictions and audit/email effects commit together. Verified-email appeal links are one-use and expire after 20 minutes, including for passwordless accounts. Reversal lifts only the selected restriction, preserves evidence and revoked sessions, and durably sends the decision notice. Exact gateway exceptions retain sensitive admission and browser origin/CSRF protection.

Current account/source checks protect seven servlet services, resource ingestion/public reads, navigation, history, realtime delivery and media. Message operations use a canonical pair lock. LiveKit signaling checks the signed token's current account/room authority before private proxying, and bounded reconciliation removes active participants. Production uses a dedicated frontend/community network, keeps direct signaling unpublished and verifies actual adapted Caddy handler order. Optional operator encryption is auth-only; missing enrollment does not prevent ordinary startup.

## Reproduced defects and corrections

Focused tests exposed and corrected concurrent block/write/history/call eligibility races, stale presence and source reads, delayed operator-directory results, concurrent pending appeals, duplicated operation responses and a refresh rollback defect. The real WebSocket regression corrected a close-frame cancellation race. Unexpected authority/fanout failures now remain observable instead of being treated as successful delivery.

Final auth review reproduced provider issuance checking access before suspension and inserting a session after its revocation scan. Issuance and USER suspension now share the existing user-row transaction boundary. The concurrent regression proves that later reinstatement cannot revive that session. Lifecycle repository lock order remains unchanged.

Hosted verification exposed a missing authoritative Study Server field in course-channel access, learner use of instructor-only announcements, ordinary SDK tokens omitting optional `nbf`, and LiveKit JS 2.20.0's Chromium codec mismatch. The source contract and fixture were corrected; token validation preserves the strict health exception, and the minimal official [2.20.1 patch](https://github.com/livekit/client-sdk-js/releases/tag/v2.20.1) fixes publication. Subsequent fixture corrections use scoped comboboxes, fresh delivered-link navigation and exact secure-cookie inventory matching rather than Playwright's HTTP loopback URL filter.

## Evidence boundary

Full backend and frontend verification passes, including 325 frontend tests, lint and unchanged core bundle caps. The hosted PostgreSQL audit test ran without skips. Native AMD64/ARM64 packaged staging passes with actual Caddy adaptation, migrations, auth startup and limited signed health signaling. Real published/received audio, removal, WebSocket closure, HTTP denial and unexpired-token reconnect denial have retained hosted evidence. The [complete hosted proof](https://github.com/Vinosaamaa/chanter/actions/runs/35776054234) passed at `23be93a8`, including real issued-cookie rejection, delivered-email appeal, operator reversal and fresh login. The new desktop/phone appeal and operator pixels were inspected, with accessibility and overflow checks passing. The final fixture also attempts an actual bounded WebSocket upgrade after suspension.

The full system review and CodeAnt dispositions distinguish tested behavior from remaining evidence. No local preview server/browser workaround was used. Production operator key provisioning, administrator enrollment and release exercise remain separate requirements; issue #249 stays open through merged-main and those gates.
