import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { backupEnvironment } from './recovery.mjs';

export function configurationBackupEnvironment(settings, environment) {
  backupEnvironment(settings, environment);
  const password = settings.CHANTER_CONFIG_BACKUP_PASSWORD;
  if (typeof password !== 'string' || Buffer.byteLength(password) < 32 || /[\r\n\0]/.test(password)
      || password === settings.CHANTER_BACKUP_CIPHER_PASS) throw new Error('Configuration backup requires a separate encryption password of at least 32 bytes');
  const endpoint = new URL(settings.CHANTER_BACKUP_S3_ENDPOINT).origin;
  return { RESTIC_REPOSITORY: `s3:${endpoint}/${settings.CHANTER_BACKUP_S3_BUCKET}/configuration/${environment}`,
    RESTIC_PASSWORD: password, AWS_ACCESS_KEY_ID: settings.CHANTER_BACKUP_S3_ACCESS_KEY,
    AWS_SECRET_ACCESS_KEY: settings.CHANTER_BACKUP_S3_SECRET_KEY, AWS_DEFAULT_REGION: settings.CHANTER_BACKUP_S3_REGION,
    GOMAXPROCS: '1', GOMEMLIMIT: '256MiB' };
}

function configurationTool(bundleDir, settings, environment) {
  const env = configurationBackupEnvironment(settings, environment);
  const executable = path.join(bundleDir, 'tools/restic');
  const checksum = fs.readFileSync(path.join(bundleDir, 'tools/restic.sha256'), 'utf8').trim();
  if (!/^[a-f0-9]{64}  restic$/.test(checksum)
      || crypto.createHash('sha256').update(fs.readFileSync(executable)).digest('hex') !== checksum.slice(0, 64)) {
    throw new Error('Configuration backup executable checksum mismatch');
  }
  return { executable, env: { PATH: process.env.PATH, ...env } };
}

export function runConfigurationBackup(bundleDir, settings, environment, snapshot, initialize = false, execute = execFileSync) {
  const { executable, env } = configurationTool(bundleDir, settings, environment);
  if (!initialize && (snapshot?.version !== 1 || !/^[a-f0-9]{40}$/.test(snapshot.release?.commit ?? '')
      || snapshot.config?.environment !== environment)) throw new Error('Invalid configuration snapshot identity');
  const input = initialize ? undefined : JSON.stringify(snapshot);
  if (input && Buffer.byteLength(input) > 256 * 1024) throw new Error('Configuration snapshot exceeds the bounded size');
  const args = initialize ? ['--no-cache', 'init', '--repository-version', '2'] : ['--no-cache', 'backup', '--stdin', '--stdin-filename', 'configuration.json',
    '--host', `chanter-${environment}`, '--tag', 'configuration', '--tag', `release-${snapshot.release.commit}`, '--json'];
  try {
    // Credentials and snapshot contents never enter argv, inherited shell or journal output.
    const output = execute(executable, args, { input, encoding: 'utf8', timeout: 600000, maxBuffer: 1024 * 1024,
      env,
      stdio: ['pipe', 'pipe', 'pipe'] });
    if (initialize) return { initialized: true };
    const summary = output.trim().split(/\r?\n/).map(line => JSON.parse(line)).find(record => record.message_type === 'summary');
    if (!summary || !/^[a-f0-9]{8,64}$/.test(summary.snapshot_id ?? '')) throw new Error();
    return { snapshotId: summary.snapshot_id };
  } catch { throw new Error('Encrypted configuration backup failed; inspect the private repository state'); }
}

export function verifyConfigurationBackup(bundleDir, settings, environment, snapshotId, expectedRelease, execute = execFileSync) {
  if (!/^[a-f0-9]{8,64}$/.test(snapshotId ?? '')) throw new Error('Invalid configuration snapshot reference');
  if (!/^[a-f0-9]{40}$/.test(expectedRelease ?? '')) throw new Error('Invalid database backup release reference');
  const { executable, env } = configurationTool(bundleDir, settings, environment);
  try {
    const snapshot = JSON.parse(execute(executable, ['--no-cache', 'dump', snapshotId, 'configuration.json'],
      { encoding: 'utf8', timeout: 60000, maxBuffer: 256 * 1024, env, stdio: ['pipe', 'pipe', 'pipe'] }));
    if (snapshot?.version !== 1 || snapshot.config?.environment !== environment
        || snapshot.release?.commit !== expectedRelease || !snapshot.runtime
        || typeof snapshot.runtime !== 'object' || Array.isArray(snapshot.runtime)) throw new Error();
    return { snapshotId, release: snapshot.release.commit };
  } catch { throw new Error('Encrypted configuration recovery verification failed'); }
}
