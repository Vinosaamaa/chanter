---
schemaVersion: 1
id: architecture-review-secure-browser-sessions
revision: 1
type: architecture-review
status: proposed
title: Secure browser sessions and transactional account recovery
repository: chanter
capabilityIds: ["identity-and-recovery"]
createdAt: 2026-09-11
reconstructed: false
confidence: medium
unknowns: ["Integrated exact-head verification is pending", "Production SMTP delivery and HTTPS deployment are unverified"]
modules: ["auth-service","gateway-service","frontend-auth"]
interfaces: ["browser-session","transactional-email"]
seams: ["smtp-provider"]
adapters: ["jdbc-session-repository","smtp","mailpit"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Browser session recovery","Refresh credential reuse detection","Device revocation","Transactional account email"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #242","url":"https://github.com/Vinosaamaa/chanter/issues/242","kind":"issue"}]
verification: {"state":"not-recorded","evidenceRefs":[]}
visibility: public-safe
publicationEligibility: eligible
issue: 242
pr: 312
release: null
run: null
---
# Secure browser sessions and transactional account recovery

## System review

The baseline combines persisted browser access tokens with memory-only refresh tokens. This exposes a bearer credential to JavaScript storage and loses renewal across reloads. Refresh rotation consumes token rows without revoking the surviving replacement when an old token is reused. The baseline email sender does not deliver account links.

The design moves renewable credentials to a scoped HttpOnly cookie, keeps access tokens only in memory, and gives each sign-in a durable session family. Rotating a token and revoking a reused family are database transactions. A failed refresh is returned only after the security state has committed. Browser origin checks and a custom request header protect cookie-changing endpoints.

## Decisions and consequences

Keep short-lived access tokens and existing service authorization boundaries. Immediate revocation across every service is deferred; previously issued access tokens can remain valid for their existing 15-minute lifetime. The session controls and operator documentation must disclose that bound.

Keep the refresh-token family expiry fixed rather than extending it on every request. Preserve consumed-token lineage so reuse remains detectable. Invalidate pre-migration credentials because they have neither trusted lineage nor the new browser transport boundary. Customers sign in once after rollout.

Use standard authenticated SMTP with transport encryption and a durable database queue. Account creation and email queue insertion commit together. Worker claims and bounded retries isolate provider failure from the account transaction. Delivery payloads contain one-time links and must be cleared after delivery or expiry; they must never enter logs.

Use a local-only Mailpit inbox for browser recovery tests. The tests must read the message actually delivered over SMTP, follow its configured link, and verify the resulting browser session. A local inbox result is not evidence of delivery by a production provider.

## Risks under review

- Concurrent tabs can consume the same refresh cookie unless cookie-changing requests are serialized.
- Late responses from the previous account can repopulate caches unless requests carry account-generation checks.
- A transaction rollback on refresh rejection can undo replay revocation.
- Sign-in and OAuth exchange need the same origin policy as refresh/logout.
- Session list/revoke must enforce ownership at the auth service.
- A worker crash releases its transaction's row lock so another worker can retry; a crash after SMTP acceptance can duplicate delivery.
- Browser traces can capture one-time account links; credential-handling journeys disable trace, video and screenshots.

## Evidence and release gates

The detailed design and verification matrix live in `docs/architecture/secure-browser-sessions-and-email.md`. `docs/operations/issue-242-change-log.md` owns the actual commands and results. This proposed record does not claim integrated, merged-main, provider or production verification. It will be finalized against the reviewed PR head.
