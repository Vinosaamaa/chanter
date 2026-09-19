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

The telemetry increment pins OpenTelemetry Java agent 2.31.1 and its published
checksum. A separate small extension uses the matching SDK 1.65.0 and permits
only bounded HTTP/database attributes and service/release/environment identity.
It removes raw span names, routes, SQL/content attributes, events, links, status
messages and vendor trace state. Public ingress strips incoming trace/baggage
headers. Production logs use a formatter that excludes messages/arguments and
keeps bounded application exception frames and validated correlation IDs.

Two real-agent Windows tests pass: decoded OTLP preserves HTTP client/server
parentage without the planted content/secret canary, and receiver outage does
not break the synthetic application or leave it waiting indefinitely. Unit
privacy tests and deployment configuration tests pass. Matching hosted native
privacy and instrumented release staging gates are added and remain pending for
this increment. Export is visibly disabled until a private HTTPS receiver and
authorization are configured. No external monitoring receipt is claimed. Metrics,
frontend error delivery, actionable alerting and complete recovery are unfinished.

The telemetry checkpoint now passes full CI, real OTLP privacy/outage tests and
instrumented native release staging on both supported architectures. The next
increment adds restic 0.19.1 with pinned native downloads and packaged binary
scanning. Each database backup references an encrypted configuration snapshot;
the periodic check decrypts that exact snapshot and validates its identity.
Bootstrap repository credentials and encryption keys stay outside the snapshot.

Review findings fixed: recovery runs on runtime pin changes, telemetry runs on
parent/common Maven and deployment wiring changes, backup health follows its own
full and every referenced dependency, and recovery fixtures remove their private
per-run state. The reported extension path mismatch is not present: Surefire uses
the telemetry module working directory. Actual decoded native OTLP assertions on
both architectures prove the privacy extension is active, including removal of
planted canaries. Configuration recovery native validation remains pending for
this increment. Full application restore and free-provider quota proof remain open.

Integration review found a configuration consistency gap: private runtime files
can be edited before deployment. Successful deployment now records their digest;
scheduled backup rejects changed state before touching the repository. This keeps
unaccepted credentials from being advertised as matching a running database.
The encrypted configuration/database drill now passes on AMD64 and ARM64.

The production integration now includes accepted native companion #316 at epoch7.
Its optional signer stays absent by default and explicitly disabled in migrators.
The runtime lane also owns the pgvector0.8.6 image addition required by #247:
separate pinned builder, no compiler in the final PostgreSQL image, extension
installation by the cluster owner before application migrations on fresh and
existing volumes, and no superuser privilege for the agent role. Recovery fixtures
now restore actual vector data alongside relational markers. The union still
requires fresh native release/recovery proof; #247 owns embedding/retrieval behavior.
