# Production foundation review

PR329 contributes to #252; remaining operational and application-recovery scope
is explicitly owned by #331 and #332. Confirmed security/data-loss defects and
failing exact-head CI continue to block merge.

Fixed review findings: owned fixture cleanup, workflow triggers for runtime pins
and shared telemetry inputs, backup dependency-chain validation, preparation of
older private state without rotating secrets, exact configuration/database/release
matching, transient recovery-secret cleanup and trace-header removal before all
public handlers. Native diagnostics also exposed a recovery timestamp rejected by
PostgreSQL startup; the validated UTC target now uses an explicit numeric offset.
Actual operator restore and private telemetry tests pass on both architectures.

Non-actionable findings checked against owning evidence:

- An initial incremental is promoted to full by pinned pgBackRest 2.59.1
  (`src/command/backup/backup.c`, no-prior-backup branch). Actual fresh release
  staging confirms that behavior; changing the command is unnecessary.
- The claimed missing libcurl dependency conflicts with the pinned source's HTTP
  implementation, successful build, executable startup and native backups.
- The Java extension path is relative to Surefire's telemetry-module working
  directory. Real decoded OTLP canary tests prove the extension loads and filters.
- Operator-selected release code is a trusted administrative input. The runbook
  requires a verified release bundle and immutable image identities. An attacker
  with write access to the operator's private code/state could replace the restore
  program itself; an additional self-verifier would not establish a new trust root.

No provider acceptance or public recovery is inferred from these dispositions.
