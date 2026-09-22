# Issue #249 CodeAnt review disposition

The complete review of PR #333 at `1c05f6d1e01e3e09c6f39962501b41561d34b5eb` finished before remediation. All five inline findings were reproduced and fixed. Exact-head re-review and the combined hosted moderation journey remain required.

## Correctness fixes

- Resource quarantine now prevents the public instructor deletion path from destroying the restricted source. The regression checks that the resource remains available in storage metadata. Account lifecycle deletion retains its separate authority and retention policy under #251.
- Auth and Study Server directory queries normalize UUID search text, so uppercase UUIDs find the same account or server.
- Operator directory view buttons are disabled during an outstanding load. A late result can no longer appear under a different selected view.
- Social realtime delivery suppresses confirmed access denial only. Authority outages and transport failures propagate instead of reporting delivery success. Tests distinguish forbidden access, HTTP 503 and failed socket writes.
- Appeal submission locks the owning restriction before checking for an existing pending appeal. Concurrent valid links cannot create competing pending reviews. Issuance retains generic responses and sends no new link while an appeal is pending.

## Custom suggestions

Accepted suggestions 3–6 replace repeated internal endpoint authentication with a private helper, query up to 100 exact source type/ID pairs in one database operation, and remove at most 100 expired appeal credentials during verified issuance. Saved appeals and audit records are retained. Suggestions 14–15 remove duplicate role checks inside transactions that already hold the operator row lock.

Suggestions 1–2 retain the existing independent IP/account or IP/token credential limits. Combining the shared limiter would change other authentication routes without an observed failure. Suggestion 7 does not introduce a general ownership framework: report scope and verified-account appeal ownership are distinct, small rules. Suggestions 8–9 retain bounded 50-row pages and a maximum offset of 10,000; indexed full-text search and cursor pagination can follow measured operator volume. Suggestion 10 retains separate account existence and restriction checks to keep their failure meanings explicit.

Suggestion 11 is rejected because the operator row lock serializes role revocation with privileged transactions. Suggestion 12 is rejected because the filter's earlier result cannot replace authorization inside the service transaction. Suggestion 13 retains separate role and private factor projections; factor ciphertext does not belong in the general operator access result.

Suggestions 16–17 and 19 retain bounded synchronous checks in servlet services and bounded I/O scheduling for realtime callers. Evidence and directory reads have explicit deadlines; common live access checks also cap concurrent requests. Changing the application to asynchronous servlet processing is not needed for this bounded launch scope. Suggestion 18 retains direct endpoint test assertions. Suggestion 20 retains the existing membership rule and aggregate lookup for an infrequent evidence read instead of duplicating its permission SQL.

The artifact-directory nitpick is not applied. The native signaling test preserves uniquely owned failure evidence, and hosted runners discard their workspace after artifacts are collected. Existing local evidence must not be recursively deleted during this task.

## Verification

Focused Maven regressions cover resource quarantine, UUID searches, exact source batching, simultaneous appeal submission, expired credential cleanup, course-channel ownership and realtime failure propagation. Operator UI tests cover delayed responses and view selection. The hosted product run passed ordinary signed-in journeys and non-web administrator bootstrap, then exposed the missing Study Server field in the course-channel access contract. That field now travels from the authoritative course repository through the message-service client, with tests at both boundaries. The moderation fixture also creates an enrolled learner discussion channel instead of selecting instructor-only announcements.

The full hosted database/Redis/Caddy/LiveKit/browser journey must still establish active audio removal, reconnect denial, the email appeal and responsive pixels before acceptance.

## Second complete review

The complete review of `d77258fa9c4363ab040562eb2548b3d849237d25` found two additional failure-propagation defects and a refresh transaction defect. Refresh now commits rotation and the current-account/profile checks together. An unexpected database failure rolls rotation back, while a replay outcome commits family revocation before the service returns HTTP 401. Browser security tests retain the concurrent replay and logout contracts. Channel authority outages propagate without discarding a valid subscription. Confirmed denial still removes it. Presence fanout failures are recorded without including identities or exception text; periodic authoritative snapshots clear stale online state when checks fail.

Call-authority outages intentionally end media access because this boundary fails closed. Reconciliation now propagates an unexpected authority failure after stopping the call, instead of reporting successful reconciliation. The suggestion to keep a call active during an authority outage is rejected. Expired or lifted restrictions remain appealable by their verified owner: a historical moderation action can still be disputed, and an appeal creates no product access. Restriction duration validation now enforces the full one-minute minimum. Hosted email selection matches the exact operation reference, including on the delivered appeal link, rather than taking the first matching subject.

The review's null-source and pair-lock nitpicks were reproduced and fixed. A source array cannot contain null, and pair authorization occurs after the serialization lock is acquired. This avoids using an authority result obtained before a potentially long lock wait. It does not claim a distributed transaction between auth and message databases. The audio proof deliberately measures cumulative received bytes and then checks their lack of increase after participant removal; it does not reconnect the SDK or infer activity from an old cumulative total.

The additional performance suggestions retain bounded source sets instead of a separate existence-query implementation, and retain indexed batches of expired credentials instead of adding another scheduled job. Operator grants are intentionally locked through privileged reads and their audit commit, including bounded source-service reads. Removing the transaction around authorization would weaken that ordering. Search indexes, database pagination for existing whole-list APIs, and aggregate projection changes are deferred to a measured scaling change; moderation checks remain batched to 100 exact IDs. The existing APIs still load their underlying lists, which is a launch-scale limitation rather than a claimed database pagination guarantee.

The remaining suggestions concern Caddy download caching, DTO naming, positional command objects, migration helper extraction, combined media/parent lookups and combined source requests. They do not correct observed behavior and are not added to this security change. Repeated active-call checks retain current authority instead of introducing a positive cache shared between participants. User-scoped operator proof cleanup is retained; its global expired-row cleanup can be bounded separately if operator volume warrants it.

The second hosted run passed saved-report reload, operator enrollment/verification, scoped case evidence and desktop/phone accessibility. All four retained Safety/operator images were inspected. Its first audio join exposed a separate real SDK contract mismatch: issued user tokens omit the optional `nbf` claim. The guard now accepts those signed, unexpired tokens while still validating `nbf` when present. The exceptional release-health path continues to require its strict bounded lifetime and disabled capabilities. A regression uses the actual product token issuer for voice and direct-call tokens.
