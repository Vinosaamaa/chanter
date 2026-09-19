---
schemaVersion: 1
id: architecture-review-durable-derived-events
revision: 1
type: architecture-review
status: proposed
title: "Database-backed delivery for Inbox and search"
repository: chanter
capabilityIds: ["chanter-social-collaboration-mvp"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Exact-head hosted gates and production release proof are pending."]
modules: ["common-events", "community-service", "message-service", "media-service", "notification-service", "search-service", "global-search"]
interfaces: ["durable-event-v1", "internal-outbox-operations", "global-search"]
seams: ["source-transaction-to-outbox", "delivery-to-consumer-cursor", "projection-to-live-source-authorization"]
adapters: ["http-event-dispatch", "http-source-visibility"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Durable Inbox delivery", "Automatic authorized search"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Durable notifications and search", "url":"https://github.com/Vinosaamaa/chanter/issues/245", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:DurableOutboxTest", "test:DurableConsumerTest", "test:OutboxDeliveryTest", "test:NotificationSmokeTest", "test:GlobalSearchSmokeTest"]}
visibility: public-safe
publicationEligibility: eligible
issue: 245
pr: null
release: null
run: null
---
# Database-backed delivery for Inbox and search

Synchronous notification HTTP calls could lose a user-visible update when a consumer was unavailable. Search required manual repair. The source databases now commit events with mutations; a lease-based dispatcher delivers them at least once through fixed HTTP destinations. This meets the current single-host requirement without operating an unused broker.

Consumers apply each source revision and its durable cursor atomically. Duplicate deliveries preserve read state, and deletion markers prevent stale content from returning. Projections do not confer permissions: search and Inbox recheck live source access and return an explicit error when that check is unavailable.

The shared queue code contains only the repeated transaction, lease, retry and cursor mechanics. Resource lifecycle, community scope and message authorization remain in their owning modules. Bounded retries, sanitized failure inspection and identity-preserving replay make outages recoverable. Compatibility epoch 5 is required because older applications cannot preserve the new delivery guarantees.

Focused regressions cover source rollback, lost acknowledgement, retry exhaustion, lease replacement, duplicate application, update/delete ordering and revoked visibility. Separate PostgreSQL and real-process restart gates establish production database and user-visible recovery behavior. Presentation evidence uses labeled fixtures; it does not prove delivery. Final hosted review and release remain pending.
