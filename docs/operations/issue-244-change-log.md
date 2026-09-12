# Issue #244: private Course Resource storage

Owning issue: [#244](https://github.com/Vinosaamaa/chanter/issues/244). Lane: media module, isolated storage/scanner integration, migration and recovery documentation. One final issue-linked PR; no external resources provisioned. #243 owns the release package and remains a deployment dependency.

## Changes

- Added immutable private S3 and explicit local adapters. Production selects S3 without fallback; OCI Always Free is the planned zero-cost provider, with compatible alternate endpoints configurable.
- Added actual byte/type/name/checksum validation, bounded spool files and verified attachment downloads under existing course authorization.
- Added Flyway V2 lifecycle state, byte reservation, scoped upload idempotency, durable worker leases, fail-closed ClamAV scanning, delayed cleanup and orphan reconciliation.
- Upload returns 202 with public `status` and `sha256`. Added metadata polling, instructor deletion and course usage routes. Storage keys, endpoints and scanner details stay private.
- Added monthly attempted-operation accounting with dedicated deletion headroom. Uncertain PUT responses preserve reservations until cleanup confirms deletion.
- AI ingestion waits for a clean scan; ingestion and deletion failures are retried by the durable worker instead of being swallowed.
- Added native architecture CI using pinned PostgreSQL16.15, S3Mock5.2.2 and ClamAV1.5.4, actual EICAR scanning and restart durability checks.

## Test evidence

- Initial validation/lifecycle/scanner tests were written before their implementations and failed at compilation. The focused suite passed after implementation.
- The blocked scanner-write regression failed after approximately 2.54 seconds against a 1-second assertion; adding the socket deadline made it pass.
- The provider-stream closure regression failed when spool creation was unavailable; closing the provider stream around spool creation made it pass.
- Local module verification covers concurrent idempotency and request caps, stale leases, deletion versus scanning/reading, real local immutable writes, corrupt content, unavailable/infected scan outcomes, uncertain PUT cleanup, preserved legacy bytes and reconciliation.
- Java21 `mvn -s backend/.mvn/settings.xml -f backend/pom.xml -pl media-service -am verify` passed: 31 media tests, with four separately gated real-process integration cases skipped locally. The final legacy-index purge received an additional focused worker regression run. Exact hosted results are recorded in PR checks.
- The new Compose file validates with Docker Compose; the new workflow passes actionlint. Container execution is delegated to hosted CI because the implementation host has no Docker daemon. No mock result is described as real scanner/provider evidence.
- The first native ARM64 job exposed that the ClamAV Alpine image has no ARM64 manifest. The suite now pins ClamAV's official Debian multi-architecture image, with AMD64 and ARM64 digests verified from the registry. The native gate remains enabled.

## Remaining release proof

The provider is unprovisioned. Native container checks must pass at the final head, followed by #243 integration, actual private bucket permissions/anonymous-denial and recovery tests, processing/failure UI browser evidence, and a measured 2 OCPU/12 GB full-stack workload. ClamAV's 4 GB container guidance cannot be added on top of the earlier 7.625 GiB base caps without reallocation. Schema V2 requires a deployment epoch boundary; old code must not be rolled back onto the new lifecycle data.

See [the operator runbook](private-course-resources.md) and [the system review](../engineering/records/architecture-review-chanter-private-resources.md).
