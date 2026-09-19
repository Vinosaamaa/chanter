# Database backup and recovery

This runbook applies to the free single-host release. Database backup and isolated
point-in-time recovery have native AMD64 and ARM64 test coverage. Off-host S3,
notification delivery and production recovery remain unverified until a real
deployment supplies those receipts. The complete application recovery procedure,
including object bytes and revocations after the recovery point, is still under
implementation in #252 and #251. Do not open a restored database to users yet.

## Configure the repository

After `host.mjs init`, edit the private `runtime/backup.env` file in the environment
state directory. Supply an HTTPS S3-compatible origin, region, private backup
bucket and credentials limited to that bucket. Use a bucket and access key
different from resource storage. Keep the generated encryption passphrase and a
copy of repository credentials in the operator's offline secret store. Losing
that passphrase makes encrypted database recovery impossible.

Use a separate repository for staging and production. The release enforces
encryption, certificate verification, one compression worker, two successful full
backup chains and WAL needed by those chains. Database archiving runs continuously
with a 60-second segment timeout. This is configuration, not proof of a five-minute
recovery point. Verify it against the actual provider.

Deployments stop ingress and writers, start persistence, verify the archive and
take a backup before migrations. A backup failure stops deployment. Do not delete
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

`backup-status.json` contains a bounded status, backup label, accepted release,
completion time and full-backup time. It contains no repository credentials or
raw provider error. A chain is stale after 30 hours without a complete backup or
eight days without a full backup. `backup STATE check` verifies continuous WAL
archiving and that freshness without creating a new backup.

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
both supported architectures. `scripts/deploy/recovery-drill.sh IMAGE` creates
three isolated fixture volumes and an encrypted local repository. It rejects a
wrong passphrase, restores two committed markers before a named recovery point,
excludes a later marker, and checks that the source cluster remains intact.
Restored PostgreSQL has no published port, no network and archiving disabled.

This fixture does not contact production, prove object recovery, measure a real
operator recovery time or authorize replacing a live volume. Preserve production
and partial restore state until a reviewed recovery procedure names the empty
destination, backup/configuration versions, permissions and deletion reconciliation.
