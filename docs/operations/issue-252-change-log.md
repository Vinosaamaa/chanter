# Issue 252 implementation

The [design and system review](../architecture/observability-and-recovery.md)
define privacy, export and recovery boundaries for the accepted free single-host
deployment. This branch is in progress; no production monitoring or recoverable
off-host backup is claimed.

The first implementation adds a validated production pgBackRest environment and
builds pinned pgBackRest 2.59.1 into the existing PostgreSQL 16 image. Source archive
SHA-256 was checked against the published release. Encryption, verified TLS,
separate repository location, one worker and retention of two successful full
chains are fixed policy. Four configuration tests pass after reproducing missing
implementation; they reject incomplete credentials, unsafe endpoints/configuration
and undersized encryption passphrases without echoing secret values.

A native AMD64/ARM64 drill passed at commit `551f773`. It uses a real PostgreSQL cluster and an
encrypted local repository, requires a wrong-key restore to fail, then restores a
second isolated cluster to a named WAL point. It checks that committed markers
before the target exist, a later marker does not, original data remains intact,
and the restored cluster cannot archive into the source repository. Full CI and
both native release staging checks passed at that commit. These are synthetic
local-repository receipts, not production recovery evidence.

Deployment now requires separate private backup S3 credentials and a generated
encryption passphrase. PostgreSQL archives WAL, and deployment checks and takes an
incremental backup before any migration. A failed backup keeps ingress stopped
and prevents SQL migration. The native release smoke exercises this path against
an encrypted local repository. Its updated native execution is pending.

Before the first migration attempt, deployment persists the target schema epoch.
A later attempt cannot cross below that floor, even when the first deployment
failed before writing a successful release receipt. An existing database volume
without migration history also fails closed. Empty historical deployments remain
valid. Focused deployment tests cover the failure ordering and recovery guards.

The scheduled operator command now checks the archive, creates a full/incremental
backup and verifies a complete, fresh chain for the current database identity.
It shares the deployment lock, uses accepted runtime configuration and records
only bounded safe status. Generated systemd timers follow the accepted release
on each run. The [backup runbook](backup-and-recovery.md) describes configuration,
schedule installation, failure handling and the limits of the current drill.
Focused lock/failure/privacy tests pass. Actual timer validation and the updated
native drill remain hosted gates for this increment.

Remaining implementation:
safe restore commands, object/configuration protection, telemetry/privacy gates,
metrics/error reporting, alert/runbook coverage and application-level recovery.
Actual S3, operator notification and measured production recovery remain separate
provider gates. No paid service has been enabled.
