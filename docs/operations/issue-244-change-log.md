# Issue #244: private Course Resource storage

Owning issue: [#244](https://github.com/Vinosaamaa/chanter/issues/244). Lane: media module, isolated storage/scanner integration, migration and recovery documentation. One final issue-linked PR; no external resources provisioned. #243 owns the release package and remains a deployment dependency.

## Changes

- Added immutable private S3 and explicit local adapters. Production selects S3 without fallback; OCI Always Free is the planned zero-cost provider, with compatible alternate endpoints configurable.
- Bound storage metadata to its endpoint/bucket or local-root identity before object access. Credential rotation preserves the binding; namespace changes require an explicit reviewed migration.
- Added actual byte/type/name/checksum validation, bounded spool files and verified attachment downloads under existing course authorization.
- Added Flyway V2 lifecycle state, byte reservation, scoped upload idempotency, durable worker leases, fail-closed ClamAV scanning, delayed cleanup and orphan reconciliation.
- Upload returns 202 with public `status` and `sha256`. Added metadata polling, instructor deletion and course usage routes. Storage keys, endpoints and scanner details stay private.
- Added monthly attempted-operation accounting with dedicated deletion headroom. Uncertain PUT responses preserve reservations until cleanup confirms deletion.
- AI ingestion waits for a clean scan; ingestion and deletion failures are retried by the durable worker instead of being swallowed.
- Clean file publication is independent of indexing: durable ingestion status retries failures while preserving `AVAILABLE` content and its byte reservation. Index failure never triggers object expiry or deletion.
- Added native architecture CI using pinned PostgreSQL16.15, S3Mock5.2.2 and ClamAV1.5.4, actual EICAR scanning and restart durability checks.
- The product stack inherits that scanner configuration and checks definition freshness. Demo seeding supplies an explicit safe filename and waits for clean availability and usable index chunks before installing grants.
- CodeAnt identified plaintext scanner transport. The default now uses a Unix socket shared only by scanner and media processes, with explicit directory/socket ownership and permissions. The optional TCP client is development-only, defaults off and rejects remote hosts; there is no fallback from Unix failure.

## Test evidence

- Initial validation/lifecycle/scanner tests were written before their implementations and failed at compilation. The focused suite passed after implementation.
- The blocked scanner-write regression failed after approximately 2.54 seconds against a 1-second assertion; adding the socket deadline made it pass.
- The provider-stream closure regression failed when spool creation was unavailable; closing the provider stream around spool creation made it pass.
- Review identified clean-file deletion risk when indexing and scan failure shared a state. The regression first failed because a learner could not read the clean resource; separate durable index work now keeps the file available across repeated index failures and retries to completion.
- Local module verification covers concurrent idempotency and request caps, stale leases, deletion versus scanning/reading, real local immutable writes, corrupt content, unavailable/infected scan outcomes, uncertain PUT cleanup, preserved legacy bytes and reconciliation.
- Java 21 affected-module verification passed after the secure-session rebase. The 39 local media tests cover index-outage preservation, legacy-index purge, HTTP delete/usage authorization, remote scanner TCP rejection, no Unix-to-TCP fallback and a real HTTP 500 server proving one attempt per S3 operation. Five separately gated real-process cases require hosted containers. Exact hosted results are recorded in PR checks.
- The namespace-change regression first failed because a changed bucket could construct a client. Startup binding now rejects bucket or endpoint changes before any network request, while normalized endpoints and rotated credentials pass.
- The new Compose file validates with Docker Compose; the new workflow passes actionlint. Container execution is delegated to hosted CI because the implementation host has no Docker daemon. No mock result is described as real scanner/provider evidence.
- The first native ARM64 job exposed that the ClamAV Alpine image has no ARM64 manifest. The suite now pins ClamAV's official Debian multi-architecture image, with AMD64 and ARM64 digests verified from the registry. The native gate remains enabled.
- Native AMD64 and ARM64 diagnostics proved clamd retained September 7 signatures after FreshClam downloaded September 10 definitions during engine startup. One-minute self-checks reported no change and did not repair the missed notification. Startup now completes a foreground update before handing control back to the upstream daemon entrypoint. The 72-hour gate stays unchanged. Explicit scan limits reject encrypted/over-limit content and avoid duplicate engines during reload; the native suite tests a compressed fixture beyond the scan limit as well as EICAR.
- Native run [34677339518](https://github.com/Vinosaamaa/chanter/actions/runs/34677339518) passed at `736301f` on AMD64 and ARM64: five actual cases across initial and preserved-volume restart phases. The [product job](https://github.com/Vinosaamaa/chanter/actions/runs/34677339521/job/103509437120) also passed all 14 browser journeys and observed the seed resource becoming available and indexed. These receipts do not establish actual OCI policy or production capacity.
- The infrastructure unit fixture originally stubbed Docker but contacted the new real scanner readiness process. It now captures that invocation within the fixture and asserts scanner service selection. Its old service-list assertion was reproduced failing before the expected list was updated. Real scanner and browser proof remain separate hosted jobs.
- The Unix transport follow-up keeps the existing deadline and protocol regressions, adds remote-TCP and fallback rejection, and requires native AMD64/ARM64 tests to use the actual Unix socket. The workflow checks its UID, group and permission bits before scanning. Earlier TCP receipts above remain historical and do not substitute for the new transport's final-head gates.

## Remaining release proof

The September 18 continuation rebases onto #319's supported backend. The Spring Boot 4 media dependency and test-import changes applied cleanly and affected-module verification passed. Full CodeAnt review completed at the saved Unix-socket head; [review dispositions](issue-244-codeant-fix.md) cover the readiness and demo-seed corrections, migration regression and unresolved production boundary. Optimized-Python regressions reproduced both readiness bypasses before explicit checks replaced assertions, and the seed regression reproduced reuse of a failed entry before filtering by status.

The integrated package scan found unused Apache 5 HTTP transport jars introduced by the AWS SDK. The media POM now excludes `apache5-client` because the adapter explicitly uses the URL-connection transport. This removes the vulnerable transitive HttpCore artifacts without suppressing advisories or changing the scanner policy.

The next review adds AI approval to demo-resource reuse and preserves explicit HTTP 503 request-budget failures through the S3 adapter and upload service. Both behaviors failed their regression tests before the changes. Failed upload cleanup keeps its byte reservation until confirmed deletion.

The provider is unprovisioned. Native container checks must pass at the final head, followed by #243 integration, actual private bucket permissions/anonymous-denial and recovery tests, processing/failure UI browser evidence, and a measured 2 OCPU/12 GB full-stack workload. ClamAV's 4 GB container guidance cannot be added on top of the earlier 7.625 GiB base caps without reallocation. Schema V2 requires deployment epoch 4, following #319's authentication epoch 3; old code must not be rolled back onto the new lifecycle data.

See [the operator runbook](private-course-resources.md) and [the system review](../engineering/records/architecture-review-chanter-private-resources.md).

## Authorization and terminal deletion correction

The independent review promoted stale AI access and late indexing after deletion to blocking defects. Live metadata now requires current availability, approval, course and viewer permission at vector/tool retrieval and the existing answer-evidence boundaries. Regression tests reproduced the missing status filter, stale tool scope and wrong-course indexed chunk before the fixes.

Agent V8 adds permanent deletion markers and serializes final chunk replacement, purge and deletion. The separate migration purge retains the resource identity without clearing a tombstone. Local tests cover both concurrent orders, purge/re-ingestion, cascading embedding removal and media reservation retention until agent deletion succeeds. The native AMD64/ARM64 workflow also exercises these races on real PostgreSQL and rechecks a persisted marker after database restart. Full final-head CI, security and review remain required.
