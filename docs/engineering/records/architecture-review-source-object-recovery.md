---
schemaVersion: 1
id: architecture-review-source-object-recovery
revision: 1
type: architecture-review
status: proposed
title: Source-owned physical mutation and object recovery closure
repository: chanter
capabilityIds: []
createdAt: 2026-09-22
reconstructed: false
confidence: high
unknowns: ["Canonical seven-source recovery and media V5 depend on accepted #251", "Complete inventory, physical object restore, original-writer closure and provider freshness remain required"]
modules: ["media-service", "deployment"]
interfaces: ["private-resource-storage", "resource-recovery-inventory"]
seams: ["physical-dispatch-to-durable-mutation", "source-owned-inventory-to-encrypted-bytes"]
adapters: ["local-private-storage", "s3-private-storage", "restic"]
relatedRecords: ["architecture-review-current-authority-recovery@1"]
decisions: []
incidents: []
features: []
capabilities: ["Bounded durable physical-operation accounting", "Private object recovery qualification"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #342","url":"https://github.com/Vinosaamaa/chanter/issues/342","kind":"issue"}]
verification: {"state":"verified","evidenceRefs":["test:StorageMutationStoreTest", "test:S3AdapterPolicyTest", "test:LocalPrivateResourceStorageTest", "docs/operations/issue-342-change-log.md"]}
visibility: public-safe
publicationEligibility: eligible
issue: 342
pr: 343
release: null
run: null
---
# Source-owned physical mutation and object recovery closure

A remote DELETE can fail locally while still running at the provider. Worker
lease expiry and an empty local scratch directory cannot establish that it will
never delete a later restored object. PUT settlement alone leaves this gap in
ordinary deletion and orphan cleanup.

The media adapter commits a bounded mutation identity before each physical PUT
or DELETE. Maintenance fencing and dispatch serialize on the same existing
budget row. Definitive completion settles only the original invocation; uncertain
outcomes survive restart and refuse another mutation of the same key. Lost
settlement commits remain outstanding. The store does not dispatch or retry work.
Adapters reject source transactions before physical I/O, preventing an independent
accounting transaction from waiting on the caller's suspended budget lock.

The verified slice includes durable store concurrency, restart, capacity and
transaction-boundary regressions plus real local and loopback S3 adapter checks.
These are evidence about local ownership accounting. They do not establish
closure of older binaries, other hosts or actual external provider requests.

The remaining inventory must stay source-owned and include every required
private or retained migration reference, exact bytes and explicit terminal
disposition. Unknown version history, missing hashes, pending source writes,
unverified current authority or incomplete references cannot qualify a restore.
The accepted encrypted byte archive supplies read-back integrity, not ownership.
The full canonical source fixture and dual-architecture PostgreSQL/object proof
remain integration requirements. Recovery and public cutover remain disabled.

The disabled metadata API now captures a temporary source-owned snapshot with
bounded reference pages. It rejects incomplete source authority, writes, leases,
namespace and current/derived Study Server scope. Source course identity and
retained migration references are explicit; source reservation is not described
as verified physical existence. Both recovery mode and explicit inventory
activation are necessary to construct it. A snapshot cannot release the global
fence, authorize a byte write, or substitute for provider/original-writer closure.
Query and strict-input regressions pass, as does affected-module verification;
independent review found no concrete blocker in this metadata boundary.
