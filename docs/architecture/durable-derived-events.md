# Durable notifications and search

Issue #245 replaces swallowed notification HTTP failures and instructor-triggered indexing. The launch keeps the existing service databases and uses bounded HTTP delivery; Redpanda is not part of this path. Source services own committed facts, notification owns Inbox state, and search owns a disposable searchable projection.

## Commit and delivery

Each source mutation and its downstream event are one database transaction. A source-local outbox contains a stable UUID, schema version, monotonic database sequence, source identity, destination, aggregate key, scope and payload. The sequence orders changes to a source aggregate whose database row is locked by the mutation. The writer rejects calls outside an active transaction. Rollback removes both source change and event.

A scheduled dispatcher claims a small batch using short database transactions and expiring leases, then makes HTTP requests outside those transactions. Destinations are fixed configuration, never URLs supplied by event payloads. Acknowledgement requires the current lease token. A crash before acknowledgement can repeat delivery; a crash after claiming becomes eligible after lease expiry. Requests have timeouts shorter than the lease. Failures back off to a bounded delay and stop after eight attempts in a visible failed state. Replay retains the original identity and records the replay timestamp/count.

Consumers serialize apply and receipt updates using a database lock and one transaction. A cursor per producer and aggregate rejects duplicate and older revisions. Deletion cursors remain after content deletion so late updates cannot restore it. The shared code is limited to this repeated queue/cursor mechanism; source-specific payloads and authorization stay in the owning services.

## Scope and projection

Search changes cover resources, approved FAQs, supported channel messages, community events and announcements. Direct messages are outside global search. Events carry stable source and Study Server/Course/Cohort/channel scope identifiers. Current source authorization is checked before content is returned to a viewer; a stale index never grants access. Deletion/archive/cancellation removes searchable content. Read paths fail closed during source authorization outages. The legacy manual replacement path must not overwrite newer per-source revisions.

Notification delivery preserves stable recipients and source identity. Duplicate delivery must preserve read/done state. Course/cohort fanout is limited to authorized members. Inbox list, unread count and read/done responses recheck source visibility so membership removal or visibility changes do not expose queued content. Retained failed events and payloads follow source database access and backup controls; operational inspection exposes identifiers/status, not message text or tokens.

## Operations and proof

Authenticated internal inspection reports pending/in-flight/failed counts, oldest pending timestamp and last successful delivery. Failed-event listing and explicit replay expose outcomes without accepting an arbitrary destination. Existing internal-route gateway restrictions remain required. No public administration UI is added.

Tests must prove rollback atomicity, actual destination outage/restart, duplicate acknowledgement loss, stale lease recovery, bounded retries, dead-letter inspection/replay, both update/delete orders and authorization revocation. Source public HTTP tests prove automatic publication; end-to-end checks prove visible search and Inbox convergence. PostgreSQL verification establishes production locking behavior separately from fast H2 tests.

Media and agent integration waits for accepted #318. Its AVAILABLE-only resource state, live viewer checks and permanent deletion markers must dominate delayed indexing. Deployment wiring is coordinated with #243; the issue remains open through merged-main and release verification.
