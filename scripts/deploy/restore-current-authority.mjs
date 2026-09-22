#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { readEnv } from './host.mjs';
import { readConfigurationBackup } from './configuration-backup.mjs';
import { isolatedRecoveryCompose, requireRecoveryCapability } from './recovery-runtime.mjs';
import { JournalRepository, journalBackupEnvironment } from './terminal-journal-storage.mjs';
import { readCurrentReplica } from './terminal-journal-replica.mjs';
import { recoverCurrentAuthority, SOURCES } from './terminal-journal-recovery.mjs';
import { lifecycleClient } from './terminal-journal-client.mjs';
import { environmentName, exactFields, nonzeroUuid, sameWatermark, validateWatermark } from './terminal-journal.mjs';

const json = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const runDocker = (args, timeout = 30_000) => execFileSync('docker', args,
  { encoding: 'utf8', timeout, maxBuffer: 512 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
const fail = () => { throw new Error('Current authority recovery failed; preserve isolated state for private inspection'); };
const inspectFormat = '{"image":{{json .Image}},"labels":{{json .Config.Labels}},"networks":{{json .NetworkSettings.Networks}},"ports":{{json .HostConfig.PortBindings}},"mounts":{{json .Mounts}}}';
const privateWrite = (file, value) => {
  const temporary = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(temporary, value, { mode: 0o600 }); fs.renameSync(temporary, file);
};
const save = (file, value) => privateWrite(file, JSON.stringify(value, null, 2) + '\n');
const empty = value => value == null || Object.keys(value).length === 0;

export function verifyRestoredDatabase(receipt, run = runDocker) {
  const value = JSON.parse(run(['inspect', '--format', inspectFormat, receipt.container]));
  const labels = JSON.parse(run(['volume', 'inspect', '--format', '{{json .Labels}}', receipt.volume]));
  if (value.image !== receipt.image || value.labels?.['chanter.recovery'] !== receipt.container
      || labels?.['chanter.recovery'] !== receipt.container || !empty(value.networks) || !empty(value.ports)
      || !Array.isArray(value.mounts) || value.mounts.some(mount => mount.Type !== 'tmpfs'
        && !(mount.Type === 'volume' && mount.Name === receipt.volume && mount.Destination === '/var/lib/postgresql/data'))
      || !value.mounts.some(mount => mount.Type === 'volume'
        && mount.Name === receipt.volume && mount.Destination === '/var/lib/postgresql/data')) fail();
}

function ownedContainers(compose, receipt, run, requireNetwork = false) {
  const raw = run(['ps', '-a', '--filter', `label=com.docker.compose.project=${compose.name}`, '--format', '{{.ID}}']).trim();
  const ids = raw ? raw.split(/\r?\n/) : [];
  if (ids.length > Object.keys(compose.services).length) fail();
  const seen = new Set();
  for (const id of ids) {
    if (!/^[a-f0-9]{12,64}$/.test(id)) fail();
    const value = JSON.parse(run(['inspect', '--format', inspectFormat, id]));
    const source = value.labels?.['com.docker.compose.service'];
    if (value.labels?.['chanter.recovery'] !== receipt.container || value.labels?.['com.docker.compose.project'] !== compose.name
        || !compose.services[source] || value.image !== compose.services[source].image || seen.has(source) || !empty(value.ports)) fail();
    if (!Array.isArray(value.mounts) || value.mounts.some(mount => mount.Type !== 'tmpfs'
        && !(source === 'postgres' && mount.Type === 'volume' && mount.Name === receipt.volume
          && mount.Destination === '/var/lib/postgresql/data'))
        || (source === 'postgres' && !value.mounts.some(mount => mount.Name === receipt.volume))) fail();
    const networks = Object.keys(value.networks ?? {});
    if (networks.some(name => name !== compose.networks.application.name) || (requireNetwork && networks.length !== 1)) fail();
    seen.add(source);
  }
  if (requireNetwork && seen.size !== Object.keys(compose.services).length) fail();
  return ids;
}

/** No migration, ingress activation or deletion of prior state. Actual source effects must be verified by private receipts. */
export async function applyRecoveryAuthority({ bundleDir, destination, settings, environment, requiredAuthority }, {
  run = runDocker, loadConfiguration = readConfigurationBackup,
  repositoryFactory = options => new JournalRepository(options), clientFactory = lifecycleClient,
} = {}) {
  environmentName(environment); validateWatermark(requiredAuthority);
  const release = json(path.join(bundleDir, 'release.json'));
  requireRecoveryCapability(release); // Old images can ignore the recovery flag. Reject before any decryption or startup.
  if (!path.isAbsolute(destination) || fs.realpathSync(destination) !== destination) fail();
  const receipt = json(path.join(destination, 'recovery.json'));
  const preview = isolatedRecoveryCompose(release, { environment, hostname: 'recovery.invalid', publicIp: '192.0.2.1' }, destination, receipt);
  const lock = path.join(destination, '.authority-lock');
  try { fs.mkdirSync(lock, { mode: 0o700 }); } catch { throw new Error('Current authority recovery is already active; inspect its lock'); }
  let compose = null, attemptDir = null, attempt = null, ownedAttempt = false;
  try {
    const snapshot = loadConfiguration(bundleDir, settings, environment, receipt.configSnapshot, release.commit);
    if (snapshot?.version !== 1 || !isDeepStrictEqual(snapshot.release, release) || snapshot.config?.environment !== environment
        || !snapshot.runtime || typeof snapshot.runtime !== 'object') fail();
    const runtime = {};
    for (const name of Object.keys(preview.services)) {
      const values = snapshot.runtime[name];
      if (!values || typeof values !== 'object' || Array.isArray(values) || Object.keys(values).length > 256) fail();
      runtime[name] = Object.entries(values).map(([key, value]) => {
        if (!/^[A-Z][A-Z0-9_]*$/.test(key) || typeof value !== 'string' || /[\r\n\0]/.test(value)) fail();
        return `${key}=${value}`;
      }).join('\n') + '\n';
    }
    const repository = repositoryFactory({ bundleDir, environment, env: journalBackupEnvironment(settings, environment) });
    const selected = readCurrentReplica(repository, [requiredAuthority]);
    verifyRestoredDatabase(receipt, run);
    for (const project of ['chanter-production', 'chanter-staging']) {
      if (run(['ps', '--filter', `label=com.docker.compose.project=${project}`, '--format', '{{.ID}}']).trim()) fail();
    }
    attemptDir = path.join(destination, `authority-${selected.manifest.checkpointId}`);
    if (fs.existsSync(attemptDir)) {
      if (fs.realpathSync(attemptDir) !== attemptDir) fail();
      attempt = json(path.join(attemptDir, 'attempt.json')); nonzeroUuid(attempt.recoveryId);
      if (attempt.release !== release.commit || attempt.container !== receipt.container
          || !sameWatermark(attempt.authority, selected.manifest.authority)) fail();
      ownedAttempt = true;
    } else {
      fs.mkdirSync(attemptDir, { mode: 0o700 });
      attempt = { schemaVersion: 1, recoveryId: crypto.randomUUID(), release: release.commit, container: receipt.container,
        authority: selected.manifest.authority, status: 'preparing', publicCutoverAllowed: false };
      save(path.join(attemptDir, 'attempt.json'), attempt);
      ownedAttempt = true;
    }
    const runtimeDir = path.join(attemptDir, 'runtime');
    if (!fs.existsSync(runtimeDir)) fs.mkdirSync(runtimeDir, { mode: 0o700 });
    if (fs.realpathSync(runtimeDir) !== runtimeDir) fail();
    for (const [name, text] of Object.entries(runtime)) privateWrite(path.join(runtimeDir, `${name}.env`), text);
    compose = isolatedRecoveryCompose(release, snapshot.config, runtimeDir, receipt);
    const composeFile = path.join(attemptDir, 'compose.json'); save(composeFile, compose);
    const command = args => run(['compose', '--project-name', compose.name, '-f', composeFile, ...args], 210_000);
    command(['config', '--quiet']);
    const existingNetwork = run(['network', 'ls', '--filter', `name=^${compose.networks.application.name}$`, '--format', '{{.Name}}']).trim();
    if (existingNetwork) {
      const network = JSON.parse(run(['network', 'inspect', '--format', '{"internal":{{json .Internal}},"labels":{{json .Labels}}}', compose.networks.application.name]));
      if (!network.internal || network.labels?.['chanter.recovery'] !== receipt.container) fail();
    }
    const prior = ownedContainers(compose, receipt, run);
    if (prior.length) run(['stop', ...prior], 60_000);
    run(['stop', receipt.container], 30_000);
    attempt.status = 'applying-isolated'; save(path.join(attemptDir, 'attempt.json'), attempt);
    for (const name of ['postgres', ...SOURCES.map(source => `${source}-service`)]) command(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', name]);
    ownedContainers(compose, receipt, run, true);
    if (run(['network', 'inspect', '--format', '{{json .Internal}}', compose.networks.application.name]).trim() !== 'true') fail();
    if (command(['exec', '-T', 'auth-service', 'java', '-cp', '/app/helpers', 'RecoveryIsolation']).trim()
        !== 'RECOVERY_SOURCE_LISTENERS_PRIVATE') fail();
    const clients = Object.fromEntries(SOURCES.map(source => [source, clientFactory({ source, environment, composeFile, project: compose.name })]));
    const checkpoint = await clients.auth.checkpoint();
    if (checkpoint !== null) { exactFields(checkpoint, ['revision', 'digest', 'checkpointId']); nonzeroUuid(checkpoint.checkpointId); }
    const authorityResult = await recoverCurrentAuthority({ repository, clients, recoveryId: attempt.recoveryId,
      requiredAuthority: selected.manifest.authority, restoredCheckpoint: checkpoint && { revision: checkpoint.revision, digest: checkpoint.digest } });
    const completed = ownedContainers(compose, receipt, run, true);
    if (run(['network', 'inspect', '--format', '{{json .Internal}}', compose.networks.application.name]).trim() !== 'true') fail();
    run(['stop', ...completed], 60_000);
    const result = { ...authorityResult, status: 'current-authority-applied-isolated', isolationVerified: true };
    save(path.join(attemptDir, 'authority-receipt.json'), result);
    attempt.status = result.status; save(path.join(attemptDir, 'attempt.json'), attempt);
    return result;
  } catch {
    if (ownedAttempt) { attempt.status = 'failed-preserved'; save(path.join(attemptDir, 'attempt.json'), attempt); }
    fail();
  } finally {
    try {
      if (compose) {
        const ids = ownedContainers(compose, receipt, run);
        if (ids.length) run(['stop', ...ids], 60_000);
      }
    } catch { fail(); }
    finally { fs.rmdirSync(lock); }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  Promise.resolve().then(async () => {
    const [bundle, bootstrap, destination, environment, minimum] = process.argv.slice(2);
    if (process.platform !== 'linux' || !minimum) throw new Error('Usage on Linux: restore-current-authority.mjs BUNDLE PRIVATE_BOOTSTRAP_ENV RESTORED_DIRECTORY ENV MINIMUM_AUTHORITY_JSON');
    const stat = fs.lstatSync(bootstrap);
    if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) fail();
    const result = await applyRecoveryAuthority({ bundleDir: path.resolve(bundle), destination: path.resolve(destination),
      settings: readEnv(bootstrap), environment, requiredAuthority: json(minimum) });
    console.log(JSON.stringify(result));
  }).catch(error => { console.error(error.message); process.exitCode = 1; });
}
