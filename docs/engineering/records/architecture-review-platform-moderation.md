---
schemaVersion: 1
id: architecture-review-platform-moderation
revision: 1
type: architecture-review
status: proposed
title: "Scoped platform moderation and live access enforcement"
repository: chanter
capabilityIds: ["chanter-social-collaboration-mvp"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Combined hosted audio/browser proof and production operator enrollment remain pending."]
modules: ["auth-service", "common-auth", "community-service", "message-service", "media-service", "realtime-service", "moderation-ui"]
interfaces: ["platform-operator-api", "moderation-reports", "current-moderation-access", "livekit-join-authorization", "verified-email-appeals"]
seams: ["reporter-to-source-evidence", "operator-session-to-case-authority", "restriction-to-current-content-access", "signed-token-to-live-room-access"]
adapters: ["fixed-service-evidence-client", "bounded-moderation-http", "livekit-participant-removal", "durable-email"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Scoped abuse investigation", "Reversible enforcement", "Suspended-account appeals"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Platform moderation scope", "url":"https://github.com/Vinosaamaa/chanter/issues/249", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:ModerationCasesTest", "test:ModerationAppealsTest", "test:MessageModerationTest", "test:StudyServerRestrictionTest", "test:RealtimeSuspensionTest", "test:RealtimeDeliveryModerationTest"]}
visibility: public-safe
publicationEligibility: eligible
issue: 249
pr: null
release: null
run: null
---
# Scoped platform moderation and live access enforcement

Chanter previously had no platform case workflow or reversible account restriction authority. Auth now stores separate operator grants, verified sessions, cases, restrictions and immutable audit records. Source services retain their permission checks and provide bounded evidence only for an authorized reporter. Assigned reviewers and administrators use an explicit investigation reason; ordinary Owners and Instructors receive no platform authority.

Restriction records preserve original content and specify exact targets, reasons and scheduled ends. Source reads, message delivery and active media recheck current authority. Signed LiveKit tokens require a separate signaling guard because removing a participant alone does not invalidate an already issued token. Bounded reconciliation removes active participants; failures do not authorize new access. Verified-email appeal credentials work without issuing a product session, including for suspended passwordless accounts.

The existing auth module and durable email queue meet this scale. A new service, broker or generic policy engine would add deployment and consistency work without an independent requirement. The tradeoff is an explicit auth availability dependency with bounded, fail-closed calls. Production integration requires epoch 9 and the release-owned signaling proxy configuration.

Focused tests reproduce role escalation, cross-scope reads, block/write races, stale presence and content visibility, factor replay, audit rollback and session revocation. Full native backend and frontend checks pass. The hosted combined test is still pending at this revision; it must establish actual audio removal, browser behavior, emailed appeal and inspected responsive pixels. Production operator bootstrap and deployment remain separate evidence, so issue #249 stays open.
