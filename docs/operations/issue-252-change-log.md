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

A native AMD64/ARM64 drill is prepared. It uses a real PostgreSQL cluster and an
encrypted local repository, requires a wrong-key restore to fail, then restores a
second isolated cluster to a named WAL point. It checks that committed markers
before the target exist, a later marker does not, original data remains intact,
and the restored cluster cannot archive into the source repository. Shell syntax
and workflow lint pass; native execution is pending.

Remaining implementation: wire private production backup credentials and timers,
safe restore commands, object/configuration protection, telemetry/privacy gates,
metrics/error reporting, alert/runbook coverage and application-level recovery.
Actual S3, operator notification and measured production recovery remain separate
provider gates. No paid service has been enabled.
