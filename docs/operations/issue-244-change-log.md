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

## Test evidence

- Initial validation/lifecycle/scanner tests were written before their implementations and failed at compilation. The focused suite passed after implementation.
- The blocked scanner-write regression failed after approximately 2.54 seconds against a 1-second assertion; adding the socket deadline made it pass.
- The provider-stream closure regression failed when spool creation was unavailable; closing the provider stream around spool creation made it pass.
- Review identified clean-file deletion risk when indexing and scan failure shared a state. The regression first failed because a learner could not read the clean resource; separate durable index work now keeps the file available across repeated index failures and retries to completion.
- Local module verification covers concurrent idempotency and request caps, stale leases, deletion versus scanning/reading, real local immutable writes, corrupt content, unavailable/infected scan outcomes, uncertain PUT cleanup, preserved legacy bytes and reconciliation.
- Java 21 affected-module verification passed after the secure-session rebase. The 37 local media tests cover index-outage preservation, legacy-index purge, HTTP delete/usage authorization and a real HTTP 500 server proving one attempt per S3 operation. Five separately gated real-process cases require hosted containers. Exact hosted results are recorded in PR checks.
- The namespace-change regression first failed because a changed bucket could construct a client. Startup binding now rejects bucket or endpoint changes before any network request, while normalized endpoints and rotated credentials pass.
- The new Compose file validates with Docker Compose; the new workflow passes actionlint. Container execution is delegated to hosted CI because the implementation host has no Docker daemon. No mock result is described as real scanner/provider evidence.
- The first native ARM64 job exposed that the ClamAV Alpine image has no ARM64 manifest. The suite now pins ClamAV's official Debian multi-architecture image, with AMD64 and ARM64 digests verified from the registry. The native gate remains enabled.
- Native AMD64 and ARM64 diagnostics proved clamd retained September 7 signatures after FreshClam downloaded September 10 definitions during engine startup. One-minute self-checks reported no change and did not repair the missed notification. Startup now completes a foreground update before handing control back to the upstream daemon entrypoint. The 72-hour gate stays unchanged. Explicit scan limits reject encrypted/over-limit content and avoid duplicate engines during reload; the native suite tests a compressed fixture beyond the scan limit as well as EICAR.

## Remaining release proof

The provider is unprovisioned. Native container checks must pass at the final head, followed by #243 integration, actual private bucket permissions/anonymous-denial and recovery tests, processing/failure UI browser evidence, and a measured 2 OCPU/12 GB full-stack workload. ClamAV's 4 GB container guidance cannot be added on top of the earlier 7.625 GiB base caps without reallocation. Schema V2 requires deployment epoch 4, following #319's authentication epoch 3; old code must not be rolled back onto the new lifecycle data.

See [the operator runbook](private-course-resources.md) and [the system review](../engineering/records/architecture-review-chanter-private-resources.md).
