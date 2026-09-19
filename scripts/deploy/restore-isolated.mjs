#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { readEnv } from './host.mjs';
import { backupEnvironment, summarizeBackup } from './recovery.mjs';
import { configurationBackupEnvironment, verifyConfigurationBackup } from './configuration-backup.mjs';
import { validateRelease } from './release.mjs';

export function selectRestoreBackup(info, label, targetTime, release) {
  if (!/^[0-9]{8}-[0-9]{6}F(?:_[0-9]{8}-[0-9]{6}[DI])?$/.test(label ?? '')) throw new Error('Invalid backup label');
  const target = Date.parse(targetTime);
  if (!Number.isFinite(target) || new Date(target).toISOString() !== targetTime || target > Date.now()) {
    throw new Error('Recovery target must be a past UTC timestamp with milliseconds');
  }
  const stanza = Array.isArray(info) ? info.find(value => value.name === 'chanter') : null;
  const chosen = stanza?.backup?.find(value => value.label === label);
  if (!chosen || !Number.isSafeInteger(chosen.timestamp?.stop) || chosen.timestamp.stop * 1000 > target) {
    throw new Error('Recovery target precedes the selected backup or the backup is missing');
  }
  const selected = summarizeBackup([{ ...stanza,
    backup: stanza.backup.filter(value => value.timestamp?.stop < chosen.timestamp.stop
      || (value.timestamp?.stop === chosen.timestamp.stop && value.label <= chosen.label)) }]);
  if (selected.label !== label || selected.backupRelease !== release.commit) throw new Error('Use the verified release bundle matching this database backup');
  return { ...selected, targetTime };
}

const docker = (args, timeout = 600000) => execFileSync('docker', args, { encoding: 'utf8', timeout,
  maxBuffer: 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

/** Database-only recovery. No application, ingress, public port or existing volume is ever selected. */
export async function restoreIsolated({ bundleDir, settings, destination, environment, label, targetTime },
    run = docker, verifyConfiguration = verifyConfigurationBackup, wait = pause) {
  const release = validateRelease(JSON.parse(fs.readFileSync(path.join(bundleDir, 'release.json'))));
  const backup = backupEnvironment(settings, environment);
  configurationBackupEnvironment(settings, environment);
  if (!path.isAbsolute(destination) || path.resolve(destination) !== destination
      || fs.realpathSync(path.dirname(destination)) !== path.dirname(destination)) {
    throw new Error('Recovery requires a new absolute directory below a real existing parent');
  }
  if (fs.existsSync(destination)) throw new Error('Recovery destination already exists; preserve and inspect it');
  for (const project of ['chanter-production', 'chanter-staging']) {
    if (run(['ps', '--filter', `label=com.docker.compose.project=${project}`, '--format', '{{.ID}}']).trim()) {
      throw new Error('Use a separate recovery host without a running Chanter environment');
    }
  }
  run(['image', 'inspect', release.images.postgres]);
  fs.mkdirSync(destination, { mode: 0o700 });
  const id = `chanter-recovery-${crypto.randomUUID()}`;
  const receipt = { version: 1, status: 'preparing', phase: 'repository-metadata', release: release.commit, image: release.images.postgres,
    container: id, volume: `${id}-data`, network: `${id}-network`, publicCutoverAllowed: false,
    pending: ['application-consistency', 'resource-objects', 'current-deletion-journal', 'session-invalidation', 'operator-review'] };
  const save = () => {
    const temporary = path.join(destination, 'recovery.json.tmp');
    fs.writeFileSync(temporary, JSON.stringify(receipt, null, 2) + '\n', { mode: 0o600 });
    fs.renameSync(temporary, path.join(destination, 'recovery.json'));
  };
  save();
  try {
    const envFile = path.join(destination, 'database-recovery.env');
    fs.writeFileSync(envFile, Object.entries(backup).map(([key, value]) => `${key}=${value}`).join('\n') + '\n',
      { flag: 'wx', mode: 0o600 });
    const constrained = ['--pull=never', '--user', '70:70', '--read-only', '--cap-drop=ALL',
      '--security-opt=no-new-privileges:true', '--memory=896m', '--cpus=1',
      '--tmpfs', '/tmp:size=32m,mode=1777', '--tmpfs', '/var/run/postgresql:size=16m,mode=1777',
      '--env-file', envFile];
    const raw = run(['run', '--rm', ...constrained, release.images.postgres, 'pgbackrest', '--output=json', 'info']);
    const selected = selectRestoreBackup(JSON.parse(raw), label, targetTime, release);
    receipt.phase = 'configuration-verification'; save();
    verifyConfiguration(bundleDir, settings, environment, selected.configSnapshot, selected.backupRelease);
    Object.assign(receipt, { backup: selected.label, configSnapshot: selected.configSnapshot, targetTime, status: 'restoring', phase: 'new-destination' });
    save();
    // Fresh unpredictable names are checked before creation. Failed state is retained,
    // never reused or recursively deleted by a later restore attempt.
    if (run(['volume', 'ls', '--filter', `name=^${receipt.volume}$`, '--format', '{{.Name}}']).trim()) throw new Error();
    if (run(['network', 'ls', '--filter', `name=^${receipt.network}$`, '--format', '{{.Name}}']).trim()) throw new Error();
    run(['volume', 'create', '--label', `chanter.recovery=${id}`, receipt.volume]);
    run(['network', 'create', '--label', `chanter.recovery=${id}`, receipt.network]);
    const volume = ['--volume', `${receipt.volume}:/var/lib/postgresql/data`];
    receipt.phase = 'database-files'; save();
    run(['run', '--rm', ...constrained, '--network', receipt.network, ...volume, release.images.postgres,
      'pgbackrest', `--set=${selected.label}`, '--type=time', `--target=${targetTime}`, '--target-action=promote', '--archive-mode=off', 'restore']);
    // WAL replay needs outbound repository access. TCP listening is disabled; only
    // the local Unix socket is available. The network is detached after promotion.
    receipt.phase = 'wal-replay'; save();
    run(['run', '-d', '--name', id, '--label', `chanter.recovery=${id}`, ...constrained,
      '--network', receipt.network, ...volume, '--entrypoint', 'postgres', release.images.postgres,
      '-D', '/var/lib/postgresql/data', '-c', 'archive_mode=off', '-c', 'listen_addresses=',
      '-c', 'shared_buffers=192MB', '-c', 'max_connections=20', '-c', 'work_mem=2MB']);
    let ready = false;
    const deadline = Date.now() + 120000;
    for (let attempt = 0; attempt < 120 && Date.now() < deadline && !ready; attempt++) {
      try {
        ready = run(['exec', id, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'chanter_admin', '-d', 'postgres', '-Atc',
          "SELECT NOT pg_is_in_recovery() AND current_setting('archive_mode')='off' AND current_setting('listen_addresses')=''"], 5000).trim() === 't';
      } catch { /* Startup or WAL replay still in progress. */ }
      if (!ready) await wait(1000);
    }
    if (!ready) throw new Error();
    run(['network', 'disconnect', receipt.network, id]);
    receipt.status = 'database-restored-isolated';
    receipt.phase = 'application-verification-pending';
    save();
    return receipt;
  } catch {
    receipt.status = 'failed-preserved';
    save();
    try {
      if (run(['inspect', '--format', '{{ index .Config.Labels "chanter.recovery" }}', receipt.container], 5000).trim() === id) {
        run(['stop', receipt.container], 15000);
      }
    } catch { /* Keep the original bounded failure; never stop an unverified container. */ }
    throw new Error('Isolated recovery failed; owned state and volumes are preserved for private inspection');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const [bundle, bootstrap, destination, environment, label, targetTime] = process.argv.slice(2);
  Promise.resolve().then(async () => {
    if (process.platform !== 'linux' || !targetTime) throw new Error('Usage on Linux: restore-isolated.mjs BUNDLE BOOTSTRAP_ENV NEW_DIRECTORY ENV BACKUP_LABEL UTC_TARGET');
    const release = JSON.parse(fs.readFileSync(path.join(bundle, 'release.json')));
    if (release.architecture !== (os.arch() === 'arm64' ? 'arm64' : 'amd64')) throw new Error('Recovery image architecture differs from the host');
    const bootstrapFile = fs.lstatSync(bootstrap);
    if (!bootstrapFile.isFile() || bootstrapFile.isSymbolicLink() || (bootstrapFile.mode & 0o077) !== 0) {
      throw new Error('Bootstrap settings must be a private regular file');
    }
    const result = await restoreIsolated({ bundleDir: path.resolve(bundle), settings: readEnv(bootstrap),
      destination: path.resolve(destination), environment, label, targetTime });
    console.log(JSON.stringify(result));
  }).catch(error => { console.error(error.message); process.exitCode = 1; });
}
