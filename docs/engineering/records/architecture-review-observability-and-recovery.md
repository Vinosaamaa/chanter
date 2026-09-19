---
schemaVersion: 1
id: architecture-review-observability-and-recovery
revision: 1
type: architecture-review
status: proposed
title: Private production telemetry and isolated recovery
repository: chanter
capabilityIds: ["production-deployment"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Complete application consistency and object recovery in #332", "Operational coverage and tested alerts in #331", "Actual provider configuration, alert receipt and off-host recovery measurements"]
modules: ["production-runtime", "shared-observability"]
interfaces: ["private-telemetry-export", "encrypted-backup-repository", "isolated-restore"]
seams: ["service-to-telemetry-provider", "postgres-to-backup-repository", "restore-to-public-cutover"]
adapters: ["opentelemetry", "pgbackrest", "restic", "s3"]
relatedRecords: ["architecture-review-chanter-free-deployment@1", "architecture-review-public-edge-and-admission@1"]
decisions: []
incidents: []
features: []
capabilities: ["Privacy-filtered operational telemetry", "Encrypted PostgreSQL recovery", "Isolated recovery acceptance"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Production observability and recovery", "url":"https://github.com/Vinosaamaa/chanter/issues/252", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["scripts/deploy/recovery.test.mjs", "scripts/deploy/restore-isolated.test.mjs", "https://github.com/Vinosaamaa/chanter/actions/runs/35423912478", "https://github.com/Vinosaamaa/chanter/actions/runs/35423912442"]}
visibility: public-safe
publicationEligibility: eligible
issue: 252
pr: 329
release: null
run: null
---
# Private production telemetry and isolated recovery

The accepted free deployment runs one PostgreSQL cluster and separate private
resource objects. Application rollback does not reverse its data or recover a lost
host. Use pinned pgBackRest for encrypted database backup and WAL recovery, with
bounded work and a repository separate from production resource objects. Use
OpenTelemetry and existing service metrics for bounded operational export.

Backup and observability configuration is operational authority. Reject incomplete
credentials, unsafe endpoints and short encryption keys without echoing secrets.
Keep private metrics/debug endpoints outside public routing. Export must omit
credentials, content, SQL values, raw URLs and arbitrary exception messages.

A restore creates a separate cluster with archive writes and customer-facing
delivery disabled. Public cutover remains blocked until database, object,
deletion/revocation and configuration checks pass. A successful backup command
alone is insufficient evidence of recoverability. Recovery targets remain
unverified until measured in an actual provider drill.

The [design](../../architecture/observability-and-recovery.md) owns the detailed
policy. Native encrypted database restore, real privacy-filtered agent export,
receiver outage and instrumented release staging pass on AMD64 and ARM64.
Matching encrypted configuration protection uses pinned restic. The real operator
restore now passes on both architectures after converting its timestamp to an
explicit numeric UTC offset. It restores relational/vector markers to the selected
time and detaches networking without authorizing public cutover. Private metric
export preserves aggregate counts and bounded runtime gauge dimensions; native
tests verify canary removal and receiver outage. Application consistency/object
recovery/current deletion authority remain in #332; complete operational coverage,
error tracking and tested alerting remain in #331. This split removes a dependency
cycle between runtime foundations, retrieval and account deletion without waiving
any launch gate. Actual provider verification remains pending. Publication eligibility means this record
is public-safe, not that the product is ready to launch.
