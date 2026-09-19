# Issue #249 system review

## Current implementation boundary

The initial foundation is implemented in auth-service: separate platform grants, password and authenticator verification, short-lived operator verification tokens, report records, assigned-case evidence reads, reversible restriction records and audit writes. Source authorization is a fixed-service contract, not a caller-selected URL. Source evidence endpoints, enforcement actions, appeals and browser workflows are still being integrated. This document does not claim issue completion or deployment.

The first focused tests reject forged role headers and ordinary accounts, validate standard authenticator vectors and encrypted enrollment secrets, reject code replay, honor role revocation, preserve restriction/audit transaction atomicity and enforce assignment before evidence reads. Message-service rejects otherwise valid suspended identities and refuses access when the authority is unavailable. A real idle WebSocket connection closes on a later suspension decision; its policy close frame is sent before receive cancellation.

## Security and failure behavior

Operator roles come from the database on every privileged request. Product roles are not inputs. Second-factor secrets use a distinct encryption key and are not written to audit records. Verification attempt limits persist through process restarts. The first administrator is created by an explicit non-web command, which refuses repeat bootstrap. Real operator enrollment and key provisioning require separate release evidence.

Suspension is checked when auth accepts an access token, refreshes a session or issues a provider session. Seven servlet services check the current account before their authenticated handlers. Realtime checks on incoming frames and every five seconds cover idle sessions. Remaining work includes recipient fanout, active calls and current source restrictions. #316 session-ID tokens and live session introspection must be integrated before claiming revoked sessions lose operator/realtime access.

Remote checks have bounded deadlines and concurrency, with no positive authorization cache. Failure prevents the protected action. Auth is consequently an explicit availability dependency for protected product requests; health probes remain independent. Isolated service tests use synthetic-user authorization only in the test profile. A real product-stack journey is still required.

PostgreSQL triggers reject audit and internal-note updates, deletion and truncation. The added PostgreSQL regression must run against the real database; an H2 pass cannot establish this protection. Database administrators who can remove triggers remain outside the application immutability guarantee. Retention and legal policy are coordinated with #251.

## Remaining acceptance work

- Resource evidence and cross-service content restriction checks. DM/message and current-member Study Server report evidence now have focused authorization tests.
- Complete block/unblock races, current presence and call behavior.
- Operator appeal resolution and broader action/notification proof. Case-scoped restriction/reinstatement and verified-email submission now have focused tests, including wrong ownership, expiry, replay and independent account/IP limits.
- Live quarantine and Study Server restrictions on public, search, media and ingestion access.
- Operator/user UI, desktop and phone interaction/pixel checks, full product restart journeys.
- Current-main integration, full backend/frontend verification, exact-head hosted gates, CodeAnt review, Engineering receipt and release proof.
