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

Answer erasure requires a causally ordered downstream receipt, not a successful transport response. The question cursor rejects stale acceptance; a separate permanent exact-answer cursor ensures later answers cannot suppress an older removal. Message status, notification retraction and agent cleanup use the existing source transactions and outboxes. Private target rows retain their original previous digest so a downstream receipt can re-run the owning mutation without creating journal authority. A changed cleanup state and its lifecycle progress event commit together. Legacy shared notification content has no recoverable AI/human provenance, so upgrade neutralizes that content while preserving navigation and user read state.

September 22 continuation: independent source review identified a shared-question pending notification preservation gap. Source normalization now retains useful ID-only updates; bounded cleanup handles older payloads without suppressing a newer human reply. Full common/agent tests and focused source/recipient tests pass. The exact Study Server DELETE route delegates first-request target moderation to the owning durable-request callback, preserving authenticated original-requester retry after terminal allocation. Real auth HTTP denial, erased-graph retry, stranger denial and first-request restriction checks pass. Final producer dispositions and retention aggregation remain under implementation; no completion or hosted union claim follows from these checks.

Explicit final/zero-content disposition review: destination totals are compared only with committed original batch records. Producer acknowledgements identify the exact original final command. Separate producer cursors cannot suppress the other producer; a missing/early final fails closed. Tests exercise missing batches, duplicate/reordered finals, zero-content producer, forged acknowledgement identity, cross-account isolation, and actual constraint-triggered rollback of recipient completion plus auth progress. Focused migrated search/notification, common protocol, source cleanup and compiled canonical helper checks pass. Other source retention/physical-object completion and full native union remain unverified.

Independent completion-scope review found that community/message finals alone omitted media resource copies and agent answer notifications. The fixed producer set is now community/message/media/agent. Media retains exact uploaded RESOURCE IDs before physical cleanup; agent retains exact STUDY_ASSISTANT_ANSWER IDs from its durable retractions. Both use the same bounded source/recipient protocol. An actual search-store regression retains a resource and PENDING account through the other three finals, then proves media erasure/fence before its final allows COMPLETE. Full common/agent and focused migrated media/search/notification checks pass, including final receipt rollback. Source physical cleanup, question-status retractions and conservative attribution/accounting remain independently pending. The unshipped media V5/agent V14 additions do not change journal/scope wire schemas or media V6 ownership.

The producer terminal-recipient fence shares the existing source-first lock order. Recipient payload retirement reuses the bounded outbox advance path and cannot be bypassed by a zero-content final. Source COMPLETE is local: the message service now proves both final acknowledgements and atomic coordinator update; it cannot complete media, agent, community or external authority gates. Focused rollback and claimed-event regressions pass. Independent/shared instructor content is preserved without inferring copied authorship.

Community source completion preserves the actual-owner boundary: content final acknowledgements alone cannot resolve restored ownership. Retained shared course payload is explicitly PRESERVED. Enrollment actor attribution becomes null while learner membership remains. Migrated store tests cover these dispositions; full hosted source aggregation remains required.

Agent terminal cleanup owns attribution removal while retaining conservative attempt claims. Its existing ledger row is the settlement serialization point; the conditional settlement cannot reintroduce metadata after attribution is cleared. No extra provider retry or budget refund is enabled. Migrated tests cover rollback, waiting settlement, stable unknown accounting, both final acknowledgements and delayed exact answer reconciliation. Full common/agent suite passed; no hosted union is inferred.

The shared agent resource erasure is invoked by canonical RESOURCE authority and authenticated media deletion. No child journal allocation or second retry mechanism is introduced. A media receipt proves exact saved-answer/status/notification reconciliation only, never physical object removal. Its append and the answer receipt transition share a transaction. The compiled hosted helper relays fixed ordinary event types through the owning authenticated controllers, preserving original event identity and keeping tokens out of output. The same-command replay is idempotent; a different producer or command is rejected.
