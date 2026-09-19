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
