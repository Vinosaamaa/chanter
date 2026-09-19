import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { configurationBackupEnvironment, runConfigurationBackup, verifyConfigurationBackup } from './configuration-backup.mjs';

const settings = {
  CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test:8443',
  CHANTER_BACKUP_S3_BUCKET: 'chanter-backups', CHANTER_BACKUP_S3_REGION: 'region-1',
  CHANTER_BACKUP_S3_ACCESS_KEY: 'private-key-canary', CHANTER_BACKUP_S3_SECRET_KEY: 'private-secret-canary',
  CHANTER_BACKUP_CIPHER_PASS: 'd'.repeat(64), CHANTER_CONFIG_BACKUP_PASSWORD: 'c'.repeat(64),
};
const snapshot = { version: 1, release: { commit: 'a'.repeat(40) }, config: { environment: 'staging' },
  runtime: { auth: { secret: 'private-runtime-canary' } } };
function fixture(t) {
  const parent = path.resolve('.cache/configuration-backup-tests');
  fs.mkdirSync(parent, { recursive: true });
  const directory = fs.mkdtempSync(path.join(parent, 'run-'));
  t.after(() => {
    assert.equal(path.dirname(fs.realpathSync(directory)), fs.realpathSync(parent));
    fs.rmSync(directory, { recursive: true });
  });
  fs.mkdirSync(path.join(directory, 'tools'));
  const content = 'verified-executable-fixture';
  fs.writeFileSync(path.join(directory, 'tools/restic'), content);
  fs.writeFileSync(path.join(directory, 'tools/restic.sha256'), crypto.createHash('sha256').update(content).digest('hex') + '  restic');
  return directory;
}

test('configuration repository separates environments and encryption passwords', () => {
  const env = configurationBackupEnvironment(settings, 'staging');
  assert.equal(env.RESTIC_REPOSITORY, 's3:https://backup.example.test:8443/chanter-backups/configuration/staging');
  assert.equal(env.GOMEMLIMIT, '256MiB');
  for (const value of ['', 'short', settings.CHANTER_BACKUP_CIPHER_PASS, 'x'.repeat(32) + '\n']) {
    assert.throws(() => configurationBackupEnvironment({ ...settings, CHANTER_CONFIG_BACKUP_PASSWORD: value }, 'staging'), /separate encryption/);
  }
});

test('backup passes private content through stdin and credentials only through a restricted environment', t => {
  const directory = fixture(t);
  const result = runConfigurationBackup(directory, settings, 'staging', snapshot, false, (executable, args, options) => {
    assert.equal(executable, path.join(directory, 'tools/restic'));
    assert.equal(JSON.stringify(args).includes('private-'), false);
    assert.deepEqual(JSON.parse(options.input), snapshot);
    assert.equal(options.env.AWS_SECRET_ACCESS_KEY, settings.CHANTER_BACKUP_S3_SECRET_KEY);
    assert.deepEqual(Object.keys(options.env).sort(), ['PATH', ...Object.keys(configurationBackupEnvironment(settings, 'staging'))].sort());
    return '{"message_type":"summary","snapshot_id":"abcdef12"}\n';
  });
  assert.deepEqual(result, { snapshotId: 'abcdef12' });
  assert.throws(() => runConfigurationBackup(directory, settings, 'staging', snapshot, false,
    () => { throw new Error('private-provider-error'); }), /^Error: Encrypted configuration backup failed/);
});

test('backup refuses corrupted tools, oversized state and invalid release before executing', t => {
  const directory = fixture(t);
  const never = () => { assert.fail('must not execute'); };
  assert.throws(() => runConfigurationBackup(directory, settings, 'staging', { ...snapshot, extra: 'x'.repeat(256 * 1024) }, false, never), /bounded size/);
  assert.throws(() => runConfigurationBackup(directory, settings, 'production', snapshot, false, never), /snapshot identity/);
  fs.appendFileSync(path.join(directory, 'tools/restic'), 'corruption');
  assert.throws(() => runConfigurationBackup(directory, settings, 'staging', snapshot, false, never), /checksum/);
});

test('recovery checks decrypt the exact database-linked snapshot without returning secrets', t => {
  const directory = fixture(t);
  const result = verifyConfigurationBackup(directory, settings, 'staging', 'abcdef12', snapshot.release.commit, (_, args) => {
    assert.deepEqual(args, ['--no-cache', 'dump', 'abcdef12', 'configuration.json']);
    return JSON.stringify(snapshot);
  });
  assert.deepEqual(result, { snapshotId: 'abcdef12', release: snapshot.release.commit });
  assert.throws(() => verifyConfigurationBackup(directory, settings, 'staging', '--latest', snapshot.release.commit, () => assert.fail()), /reference/);
  for (const output of ['private-error', JSON.stringify({ ...snapshot, config: { environment: 'production' } }),
    JSON.stringify({ ...snapshot, release: { commit: 'b'.repeat(40) } })]) {
    assert.throws(() => verifyConfigurationBackup(directory, settings, 'staging', 'abcdef12', snapshot.release.commit, () => output), /recovery verification failed/);
  }
});
