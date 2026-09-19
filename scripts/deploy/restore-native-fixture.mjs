/** Native CI adapter: encrypted POSIX repositories replace external S3 only at the process boundary. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { imageNames } from './release.mjs';
import { restoreIsolated } from './restore-isolated.mjs';
import { verifyConfigurationBackup } from './configuration-backup.mjs';

const [operation, state, image, restic, repository, targetTime] = process.argv.slice(2);
const execute = (file, args, options = {}) => execFileSync(file, args, { encoding: 'utf8', timeout: 600000,
  maxBuffer: 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'], ...options });
const bundleDir = path.join(state, 'bundle');
const commit = 'a'.repeat(40);
if (operation === 'prepare') {
  const imageId = execute('docker', ['image', 'inspect', '--format', '{{.Id}}', image]).trim();
  const release = { version: 1, commit, architecture: process.arch === 'arm64' ? 'arm64' : 'amd64', schemaEpoch: 7,
    images: Object.fromEntries(imageNames.map(name => [name, imageId])) };
  fs.mkdirSync(path.join(bundleDir, 'tools'), { recursive: true, mode: 0o700 });
  fs.writeFileSync(path.join(bundleDir, 'release.json'), JSON.stringify(release), { mode: 0o600 });
  const executable = path.join(bundleDir, 'tools/restic');
  fs.copyFileSync(restic, executable); fs.chmodSync(executable, 0o700);
  fs.writeFileSync(path.join(bundleDir, 'tools/restic.sha256'), crypto.createHash('sha256').update(fs.readFileSync(executable)).digest('hex') + '  restic', { mode: 0o600 });
  const snapshot = { version: 1, release, config: { environment: 'staging' }, runtime: { fixture: 'private-recovery-canary' } };
  const output = execute(restic, ['--no-cache', 'backup', '--stdin', '--stdin-filename', 'configuration.json', '--host', 'chanter-drill', '--json'],
    { input: JSON.stringify(snapshot) });
  const id = output.trim().split(/\r?\n/).map(JSON.parse).find(row => row.message_type === 'summary').snapshot_id;
  assert.match(id, /^[a-f0-9]{8,64}$/);
  process.stdout.write(id);
} else if (operation === 'exercise') {
  const release = JSON.parse(fs.readFileSync(path.join(bundleDir, 'release.json')));
  const backupInfo = JSON.parse(fs.readFileSync(path.join(state, 'backup-info.json')));
  const label = backupInfo.find(stanza => stanza.name === 'chanter').backup.at(-1).label;
  const settings = { CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test', CHANTER_BACKUP_S3_BUCKET: 'native-fixture',
    CHANTER_BACKUP_S3_REGION: 'fixture-region', CHANTER_BACKUP_S3_ACCESS_KEY: 'fixture-key', CHANTER_BACKUP_S3_SECRET_KEY: 'fixture-secret',
    CHANTER_BACKUP_CIPHER_PASS: 'd'.repeat(64), CHANTER_CONFIG_BACKUP_PASSWORD: 'e'.repeat(64) };
  const run = (original, timeout = 600000) => {
    const args = [...original];
    if (args[0] === 'run') {
      const envIndex = args.indexOf('--env-file');
      assert.ok(envIndex > 0); args[envIndex + 1] = path.join(state, 'backup.env');
      const imageIndex = args.indexOf(release.images.postgres);
      assert.ok(imageIndex > 0); args.splice(imageIndex, 0, '--volume', `${repository}:/var/lib/pgbackrest/repo:ro`);
    }
    return execute('docker', args, { timeout });
  };
  const verify = (...args) => verifyConfigurationBackup(...args, (file, command, options) => execute(file, command,
    { ...options, env: { ...options.env, RESTIC_REPOSITORY: process.env.RESTIC_REPOSITORY, RESTIC_PASSWORD: process.env.RESTIC_PASSWORD } }));
  let result;
  try {
    result = await restoreIsolated({ bundleDir, settings, destination: path.join(state, 'operator-restore'),
      environment: 'staging', label, targetTime }, run, verify);
  } catch (error) {
    const receipt = JSON.parse(fs.readFileSync(path.join(state, 'operator-restore/recovery.json')));
    console.error(`Native operator recovery failed during ${receipt.phase}`);
    try {
      const capture = spawnSync('docker', ['logs', '--tail', '100', receipt.container],
        { encoding: 'utf8', timeout: 5000, maxBuffer: 256 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
      const logs = `${capture.stdout ?? ''}\n${capture.stderr ?? ''}`;
      console.error(JSON.stringify({ nativeRecoveryDiagnostics: {
        captured: capture.status === 0, logBytes: Buffer.byteLength(logs),
        sourceConnectionsHigher: /max_connections.*lower|insufficient parameter settings/s.test(logs),
        missingRecoveryTarget: /recovery ended before configured recovery target was reached/.test(logs),
        permissionFailure: /Permission denied/.test(logs),
        readOnlyFailure: /Read-only file system/.test(logs),
        invalidSetting: /unrecognized configuration parameter|invalid value for parameter|invalid input syntax/.test(logs),
        invalidParameter: /(?:unrecognized configuration parameter|invalid value for parameter) "([a-z_]+)"/.exec(logs)?.[1] ?? null,
        fatalCategory: /(?:FATAL:|PANIC:)\s+([^\n]+)/.exec(logs)?.[1]?.replace(/"[^"]*"|'[^']*'/g, '[redacted]').slice(0, 240) ?? null,
        recoveryComplete: /archive recovery complete|ready to accept connections/.test(logs),
        missingWal: /unable to find|could not restore|not found in the archive/.test(logs),
        sharedMemoryFailure: /shared memory|No space left/.test(logs),
        missingFile: /No such file or directory/.test(logs),
        authenticationFailure: /authentication failed|no pg_hba.conf entry/.test(logs),
        fatal: /FATAL:|PANIC:/.test(logs),
      } }));
    } catch { console.error('Native recovery container diagnostics unavailable'); }
    throw error;
  }
  assert.equal(result.publicCutoverAllowed, false);
  const query = sql => execute('docker', ['exec', result.container, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'chanter_admin', '-d', 'postgres', '-Atc', sql]).trim();
  assert.equal(query('SELECT string_agg(id::text, chr(44) ORDER BY id) FROM recovery_marker'), '1,2');
  assert.equal(query('SELECT embedding::text FROM vector_marker'), '[1,2,3]');
  assert.equal(execute('docker', ['inspect', '--format', '{{json .NetworkSettings.Networks}}', result.container]).trim(), '{}');
  const ports = JSON.parse(execute('docker', ['inspect', '--format', '{{json .HostConfig.PortBindings}}', result.container]));
  assert.equal(Object.keys(ports ?? {}).length, 0);
  console.log('Operator restore command recovered relational/vector data, matched encrypted configuration and detached networking.');
} else throw new Error('Unknown native recovery fixture operation');
