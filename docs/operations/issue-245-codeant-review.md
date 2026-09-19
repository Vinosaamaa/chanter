# Issue 245 review decisions

Full CodeAnt review of PR #327 at `62d7b61` completed. Its four inline findings were accepted:

- The outage drill now records process start identity at launch and uses a Linux process descriptor when signalling. Missing, stale and wrong-command receipts fail closed; no port-based fallback exists.
- An open search repeats its unchanged query five seconds after each completed request. Requests do not overlap; closing or editing the query cancels subsequent refreshes. A failing regression reproduced the stale-result bug before the change.
- Legacy reindex filters event-managed sources inside the batch insert, retaining the shared consumer lock without one cursor query per candidate.
- Event fanout selects authorized Course recipients or enrolled Cohort learners in one query. It avoids per-member authorization calls and repeated enrollment counts, and retains all recipients beyond the previous 200 boundary.

The additional review suggestions were assessed together. Static producer validation, a shared notification identity factory, expired-lease indexes in all three source migrations, and request-local source visibility reuse are implemented. Visibility is checked again on the next request, so revocation is not hidden by a cross-request cache. The source-outage response remains an explicit error; returning an invented zero unread count would be incorrect.

The short global consumer transaction is intentional for the current single-host launch. PostgreSQL tests prove concurrent claim and duplicate-apply behavior. Per-aggregate concurrency is deferred until measured contention requires it. The dispatcher already has a dedicated scheduler, separate from media jobs; it claims one delivery at a time, times out each request, and bounds a pass to 25. Parallel workers would add concurrent delivery load without evidence that this launch needs it.

Recipient events retain individual identities and atomic source transactions. Bulk write optimization can follow measured fanout pressure; introducing a second asynchronous recipient-expansion job would change the accepted delivery contract. Durable retention remains owned by #251, with delivered payload text already removed and failed payloads retained for explicit replay. A cleanup policy must preserve deletion cursors and cannot silently discard failed work.

The remaining suggestions concern structural refactoring: splitting the concrete PostgreSQL scenario, new payload builders, replacing the existing notification interface, and avoiding one post-mutation source read. They are deferred. Current typed payloads, source integration tests and the compact shared outbox define the needed boundary; the read within the write transaction provides the committed source snapshot on both H2 and PostgreSQL. These changes would not address a demonstrated correctness failure.

The initial actual-database outage/restart drill passed. Its browser follow-through found an ambiguous Inbox locator, now scoped to the thread list. Hosted fixture images were inspected at 390 and 1280 pixels. Final remediation-head gates and full review must pass before integration; their exact commit and artifact receipt belongs on PR #327.
