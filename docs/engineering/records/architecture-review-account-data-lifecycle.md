---
schemaVersion: 1
id: architecture-review-account-data-lifecycle
revision: 1
type: architecture-review
status: proposed
title: "Private account exports and durable terminal recovery authority"
repository: chanter
capabilityIds: []
createdAt: 2026-09-22
reconstructed: false
confidence: medium
unknowns: ["Coordinated deletion, preservation holds and ownership resolution remain incomplete.", "Six source recovery handlers and agent invalidation remain incomplete.", "Hosted browser, PostgreSQL cancellation and final dependency integration are pending.", "External journal replication, retention and reviewed legal contacts require their owning operational evidence."]
modules: ["auth-service", "community-service", "message-service", "media-service", "agent-service", "notification-service", "search-service", "common", "gateway-service", "frontend"]
interfaces: ["backend/common/src/main/java/com/chanter/common/lifecycle/AccountExportProtocol.java", "backend/common/src/main/java/com/chanter/common/lifecycle/TerminalJournal.java", "docs/operations/terminal-journal-contract.md"]
seams: ["auth-export-to-source-snapshot", "browser-download-to-live-session", "canonical-journal-to-source-terminal-authority", "external-recovery-to-committed-receipt"]
adapters: ["backend/auth-service/src/main/java/com/chanter/auth/lifecycle/ExportSourceClient.java"]
relatedRecords: ["architecture-review-durable-derived-events@1", "architecture-review-native-subscription-companion@1"]
decisions: []
incidents: []
features: []
capabilities: ["Private bounded source exports", "Single-use browser download authorization", "Canonical terminal journal recovery", "Durable restored-session invalidation"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Account lifecycle issue", "url":"https://github.com/Vinosaamaa/chanter/issues/251", "kind":"issue"}, {"label":"Complete application recovery", "url":"https://github.com/Vinosaamaa/chanter/issues/332", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:ExportSourceExecutionTest", "test:AccountExportDownloadHttpTest", "test:TerminalJournalStoreTest", "test:AuthTerminalRecoveryTest", "test:RecoveryInvalidationStoreTest", "test:AccountDataPage.test.tsx"]}
visibility: public-safe
publicationEligibility: eligible
issue: 251
pr: 335
release: null
run: null
---
# Private account exports and durable terminal recovery authority

Auth owns export request identity, recent-login authority, readable progress and private download access. Each data owner captures named personal fields into bounded private pages and commits its receipt through the existing durable outbox. Export paths and upstream destinations are fixed. Shared content and saved answers retain current authorization and exact content-digest checks when downloaded. Source copies expire after twenty-four hours; a failed, incomplete or revoked export never becomes a complete archive.

Source execution has one admitted capture, bounded same-event waiters and four retained reads. A whole-operation deadline covers database and network work. Capacity returns only after the transaction and connection have closed. The existing dispatcher owns retries, so a caller timeout does not introduce another durable queue. Slow-stream and blocked-query tests verify rollback, connection release, later-account admission and same-event replay.

The browser requests a one-use navigation grant through an authenticated, CSRF-protected POST. Only its hash is stored, with exact account, job, durable session and original access expiry. The HttpOnly Secure SameSite=Strict cookie has one job path and at most sixty seconds of life. GET consumes it atomically and rechecks current session and job between archive pages. The current refresh cookie must identify the same session. The gateway delegates only the exact GET route without a query and strips spoofed identity headers. A failed or partial transfer consumes the grant and omits the ZIP end marker. The browser requests another grant explicitly; it never assembles an account-sized Blob or puts a secret in the URL.

Terminal recovery uses one contiguous committed SHA-256 journal with a serialized head. Auth imports the original event identities, timestamps and chain in the same transaction as its participant effects. Later appends extend the recovered history. Reapply records terminal target authority and a committed prefix separately from physical cleanup. Auth closes restored account sessions and exports while reporting retained identity cleanup as PENDING. A separate operation invalidates all restored browser credentials and records an idempotent recovery ID at the exact canonical and participant watermark. A rollback removes the effects and receipt together.

The application does not infer deletion completion from database access closure or a backup schedule. External replication must acknowledge a verified recoverable prefix before completion can be claimed. Restore remains closed to public writers until the recovery owner verifies every source and invalidation receipt. The other source handlers, current moderation-authority integration, preservation controls, ownership choices, epoch 10 runtime compatibility and complete deletion tests remain required work in this draft.

The normal source deletion coordinator extends the same existing outbox rather than adding retries. A local source request is access closure, not canonical authority or completed erasure. Auth producer/kind validation, atomic allocation/dispatch, exact receipt authority and idempotent source retry have focused rollback/race coverage. All completion claims remain gated by downstream cleanup and external journal replication. The cross-service fixture and normal scope distribution remain pending.
