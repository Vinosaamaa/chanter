---
schemaVersion: 1
id: architecture-review-chanter-private-resources
revision: 1
type: architecture-review
status: proposed
title: Private Course Resource lifecycle and free storage boundary
repository: chanter
capabilityIds: ["private-course-resources"]
createdAt: 2026-09-12
reconstructed: false
confidence: high
unknowns: ["Unprovisioned OCI credentials and private bucket policy", "Full 2 OCPU and 12 GB workload capacity with ClamAV", "Provider recovery and browser processing-state acceptance"]
modules: ["media-service"]
interfaces: ["course-resource-api", "private-object-storage", "malware-scanner", "resource-chunk-ingestion"]
seams: ["object-write-and-database-reservation", "scan-and-delete-concurrency", "deployment-schema-epoch"]
adapters: ["s3-compatible-object-storage", "local-private-files", "clamav-instream", "postgresql"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Private resource storage", "Fail-closed malware scanning", "Conservative free-tier accounting"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #244","url":"https://github.com/Vinosaamaa/chanter/issues/244","kind":"issue"},{"label":"Oracle Always Free limits","url":"https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm","kind":"documentation"},{"label":"ClamAV Docker guidance","url":"https://docs.clamav.net/manual/Installing/Docker.html","kind":"documentation"}]
verification: {"state":"verified","evidenceRefs":["issue:244", "backend/media-service/src/test/java/com/chanter/media/application", "docs/operations/issue-244-change-log.md"]}
visibility: public-safe
publicationEligibility: eligible
issue: 244
pr: null
release: null
run: null
---
# Private Course Resource lifecycle and free storage boundary

## Context and decision

The previous upload path wrote local bytes and exposed metadata immediately. There was no durable quarantine, real malware gate, failed-write reconciliation or object budget. Moving the same behavior to an object-store URL would preserve those flaws and bypass course authorization on downloads.

The media module now owns a small private-storage interface and a transactional lifecycle repository. Object bytes stay behind the module. Upload reserves bytes and an immutable key before making a network call; a clean scan is required before publication. Each external operation occurs outside database transactions. Completion compares a durable lease identifier and current state so a deletion or newer worker cannot be overwritten by a stale scanner.

The application has four public statuses, while its internal states distinguish interrupted writes, quarantine, active scan, failure and pending cleanup. This distinction is required for recovery, but object keys and lease states never appear in the API. Idempotency is scoped to uploader plus UUID and binds the complete validated payload. A rejected or deleted retry returns the original identity instead of silently creating a new object.

## Alternatives

- Public or signed object links would move the authorization boundary away from current course permissions. Verified application downloads avoid that change and are practical at the 10 MiB limit.
- Scanning synchronously inside upload would make scanner downtime an HTTP timeout without durable recovery. A database-backed worker keeps files quarantined and retries safely.
- A new queue or workflow service would add another durable infrastructure dependency. PostgreSQL leases are enough for this small launch tier.
- R2 as the mandatory provider would conflict with the owner's no-charge instruction because usage above its allowance is billed. A configurable S3 adapter with an unupgraded Oracle Always Free account is the planned path; no provider is provisioned by this PR.
- Adding a 4 GiB scanner to the prior 7.625 GiB runtime caps would leave insufficient OS memory on the chosen 12 GB VM. Capacity remains a measured release gate, with no paid fallback.

## Failure and privacy review

Declared length, filename extension and MIME are checked against bounded actual bytes. SHA-256 persists with metadata, and each download spools one object read and verifies the size and hash before the response can contain file content. Quarantined, unauthorized, corrupt, rejected and deleted objects are not downloadable. Private spools are bounded, closed on failure, and removed after use or age-based recovery.

ClamAV must supply a clean verdict using fresh definitions. Unavailable, malformed or stale responses fail closed. A regression exposed a socket timeout gap: read timeouts do not cover a blocked upload write. Closing the channel on a total deadline now bounds connection, reads and writes. The scanner defaults to Unix IPC because its TCP protocol lacks encryption and authentication. Only the media and scanner processes share the socket directory, with mode 2770 and a group-restricted 0660 socket. Optional development TCP is limited to loopback and disabled by default; Unix errors never trigger a network fallback. A clean file becomes available independently of the AI index; separate durable ingestion status retries index failures without deleting valid files. Deletion retries object and AI-chunk cleanup before releasing reserved bytes.

The database reserves at most 8 GB of resource bytes. It commits every S3 attempt before network I/O and disables SDK retries. Normal uploads, reads and listings share 36,000 monthly operations; 4,000 operations remain reserved for deletion. Uncertain calls never refund operation counts. These limits control this module, not other provider clients. Account backup space, inventory reconciliation after restore, and provider limits remain operator responsibilities.

A persisted namespace fingerprint binds the database to the normalized endpoint and bucket, or the local root, before the adapter can issue requests. Credentials do not affect the identity. This prevents a configuration change from treating missing objects in a different bucket as successful deletion and releasing real reservations. Changing the namespace requires a reviewed copy, checksum inventory and explicit binding update under maintenance; startup never rebinds automatically.

## Migration and release boundary

V2 quarantines old metadata and preserves its storage reservation. Opt-in migration validates legacy files, persists the destination key before copying, confirms interrupted writes and scans the copy. Original files stay available to recovery operators. Legacy AI chunks must be cleared during maintenance because they predate the new scanning guarantee.

Older code ignores the new states and cannot be a safe rollback target. Deployment must advance the schema epoch when adopting V2. Full recovery requires a matching metadata/file snapshot, conservative operation counters, and an inventory review before workers or uploads resume.

## Evidence limits

The local filesystem adapter trusts the operating-system account that owns its private root. Preliminary symlink checks reject static substitutions but do not isolate a hostile process with that account's filesystem authority. Production selects S3.

Remote indexing may continue after a caller timeout and create chunks after deletion. Media completion leases cannot prevent those writes. Strict authorization of currently available, AI-approved resources and durable ingestion/deletion coordination remain production gates under #246/#251.

Local tests exercise the real lifecycle SQL using H2, real filesystem bytes, scanner socket protocol, concurrency and failure injection. A separate native AMD64/ARM64 workflow runs real PostgreSQL, S3Mock and ClamAV, tests EICAR rejection and restarts processes with preserved volumes. S3Mock does not establish OCI IAM behavior. Actual private-provider access, constrained full-stack workload and user-facing browser acceptance remain required before deployment completion.
