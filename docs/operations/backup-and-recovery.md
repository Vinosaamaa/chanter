# Database backup and recovery

This runbook applies to the free single-host release. Database backup and isolated
point-in-time recovery have native AMD64 and ARM64 test coverage. Off-host S3,
notification delivery and production recovery remain unverified until a real
deployment supplies those receipts. The complete application recovery procedure,
including object bytes and revocations after the recovery point, is still under
implementation in #252 and #251. Do not open a restored database to users yet.

## Configure the repository

For a state directory created before this release, run
`node scripts/deploy/host.mjs prepare-recovery /srv/chanter/production` first.
It adds only missing backup/telemetry settings under the deployment lock and
preserves existing keys, credentials and application files. Repeating it is safe.
It neither initializes a remote repository nor deploys unconfigured settings.

After `host.mjs init`, edit the private `runtime/backup.env` file in the environment
state directory. Supply an HTTPS S3-compatible origin, region, private backup
bucket and credentials limited to that bucket. Use a bucket and access key
different from resource storage. Keep the generated encryption passphrase and a
copy of repository credentials in the operator's offline secret store. Preserve
both `CHANTER_BACKUP_CIPHER_PASS` and the independently generated
`CHANTER_CONFIG_BACKUP_PASSWORD`. Losing either key prevents complete recovery.
The configuration archive deliberately excludes these bootstrap secrets.

Initialize the configuration repository once from the verified release bundle:

```sh
node scripts/deploy/host.mjs init-config-backup "$PWD" /srv/chanter/production
```

This explicit operation creates a restic version 2 repository under
`configuration/production` in the private backup bucket. Deployment never treats
an inaccessible repository as an empty one. An existing repository must remain
intact; repair credentials or connectivity instead of initializing a replacement.
The release includes a checksum-pinned native restic executable and verifies its
checksum before use. Release checks also scan that executable for vulnerabilities.

Use a separate repository for staging and production. The release enforces
encryption, certificate verification, one compression worker, two successful full
backup chains and WAL needed by those chains. Database archiving runs continuously
with a 60-second segment timeout. This is configuration, not proof of a five-minute
recovery point. Verify it against the actual provider.

Deployments stop ingress and writers, start persistence, verify the archive and
take a backup before migrations. Each database backup is annotated with the exact
encrypted configuration snapshot containing runtime secrets, configuration,
release identity and the previous migration floor. Contents pass through stdin
without plaintext archive files. Before a migration, the snapshot describes the
requested release configuration; it does not assert the database already has that
release's schema. A backup failure stops deployment. Do not delete
the deployment lock or migration floor to bypass a failure. Establish whether the
owner is running, inspect the private repository, and fix forward.

## Schedule and inspect backups

The examples use `/srv/chanter/production` as the operator-created state directory.
Run the commands from the extracted, verified release bundle on the Linux host.
Use Node 24 from a stable installation path. Generated units record that actual
executable; regenerate them when moving the Node installation. Keep bundle
directories and private state owned by the operator and inaccessible to application users.

```sh
node scripts/deploy/host.mjs backup /srv/chanter/production full
node scripts/deploy/host.mjs backup-schedule /srv/chanter/production
sudo install -m 0644 /srv/chanter/production/systemd/chanter-backup-* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now chanter-backup-full.timer chanter-backup-incr.timer chanter-backup-check.timer
sudo systemctl list-timers 'chanter-backup-*'
```

Full backups run Sundays at 02:00 UTC, incrementals Monday through Saturday at
02:00 UTC, and archive/chain checks every ten minutes. Timers catch up after a
restart. Backup start times include a small randomized delay. The stable launcher
reads the accepted release receipt on every run, so it follows later successful
deployments. Failed or incomplete deployments cannot become scheduled backup
owners. Switching environments requires disabling the old timers and generating
the new environment's units. Only one environment may occupy the free host.

The accepted deployment receipt fingerprints its configuration and runtime secret
files. Editing those files before a successful deployment makes scheduled backup
fail closed: pending credentials must not be recorded as the running database's
matching configuration. Deploy the reviewed configuration to resume this check.

`backup-status.json` contains a bounded status, backup label, accepted release,
completion time, matching configuration snapshot and full-backup time. It contains no repository credentials or
raw provider error. A chain is stale after 30 hours without a complete backup or
eight days without a full backup. `backup STATE check` verifies continuous WAL
archiving, all declared dependencies of the selected chain, and freshness. It also
decrypts and validates the referenced configuration snapshot without returning its
contents. It does not create a new backup. This metadata and configuration check
does not replace a real PostgreSQL restore or detect every damaged data block.

```sh
node scripts/deploy/host.mjs backup /srv/chanter/production check
sudo journalctl -u chanter-backup-full -u chanter-backup-incr -u chanter-backup-check
```

Timer success is not recovery proof. Alert receiver integration and a measured
off-host restore are required before launch. If a check fails, inspect the
repository and deployment privately, check available host and bucket capacity,
and verify the most recent successful chain. Avoid posting credentials, runtime
environment files or raw provider diagnostics in issues.

## Rehearse database recovery

The repository's `Recovery drill` workflow builds the pinned PostgreSQL image on
both supported architectures. `scripts/deploy/recovery-drill.sh IMAGE RESTIC` creates
three isolated fixture volumes and an encrypted local repository. It rejects a
wrong passphrase, restores two committed markers before a named recovery point,
excludes a later marker, and checks that the source cluster remains intact.
Restored PostgreSQL has no published port, no network and archiving disabled.
The drill also creates a real encrypted configuration archive, rejects its wrong
key and restores the exact snapshot linked to the database backup. On exit it
removes only its owned Docker fixtures and verified per-run private directory.

Configuration snapshots currently remain retained; automated deletion awaits a
policy that preserves every configuration referenced by retained database chains.
The actual provider's storage and request quotas, including continuous WAL and
periodic checks, must fit the free allocation before scheduling production work.
No paid overage or provider recovery guarantee is implied by these fixtures.

This fixture does not contact production, prove object recovery, measure a real
operator recovery time or authorize replacing a live volume. Preserve production
and partial restore state until a reviewed recovery procedure names the empty
destination, backup/configuration versions, permissions and deletion reconciliation.

## Restore onto a separate operator host

Use the verified release bundle named in the selected backup's `release`
annotation. Verify `images.sha256` before loading `images.tar` with Docker. The
command requires that immutable PostgreSQL image to be present; it does not pull
an arbitrary image or select an existing volume. Supply the offline bootstrap
backup settings as a private environment file, including both encryption keys.

```sh
sha256sum --check images.sha256
docker load --input images.tar
node scripts/deploy/restore-isolated.mjs "$PWD" /srv/recovery/bootstrap.env \
  /srv/recovery/attempt-01 production BACKUP_LABEL 2026-09-19T00:00:00.000Z
```

Replace the label and timestamp with reviewed values from the repository. The
target must be after the selected backup completed. The command refuses an
existing destination or a host running a Chanter staging/production environment.
It validates the selected dependency chain and decrypts its exact configuration,
checking the release identity, before creating a fresh named volume and network.
PostgreSQL listens only on its Unix socket. No port is published. WAL replay can
read the repository; after promotion the command disconnects networking and
confirms archive writes remain disabled.

`recovery.json` records owned resource names, the safe operation phase and pending
checks. A failure retains the directory and volumes and stops only a container
whose ownership label matches this attempt. Do not rerun against that directory.
Inspect the named resources privately and use a new reviewed destination for a
later attempt. The native fixture tests this same operator command with encrypted
local repositories at the storage boundary; external S3 behavior remains a
separate provider gate.

`database-restored-isolated` means only that database replay completed. It never
allows public cutover. Application consistency, matching object bytes, the
current terminal-deletion journal, invalidation of restored sessions/native
requests and operator review remain mandatory. Do not connect application services
or expose the restored database while any of these checks is incomplete.
