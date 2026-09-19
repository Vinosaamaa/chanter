import test from 'node:test';
import assert from 'node:assert/strict';
import { assertMigrationFloor, backupEnvironment, backupUnits, summarizeBackup } from './recovery.mjs';

const configured = () => ({
  CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test',
  CHANTER_BACKUP_S3_BUCKET: 'chanter-backups',
  CHANTER_BACKUP_S3_REGION: 'region-1',
  CHANTER_BACKUP_S3_ACCESS_KEY: 'fixture-access',
  CHANTER_BACKUP_S3_SECRET_KEY: 'fixture-secret',
  CHANTER_BACKUP_CIPHER_PASS: 'fixture-independent-backup-passphrase-32bytes',
});

test('backup timer commands reject shell and systemd substitutions in operator paths', () => {
  for (const state of ['relative', '/srv/a b', '/srv/a%h', '/srv/$HOME', '/srv/a\nExecStart=bad', '/srv/../data']) {
    assert.throws(() => backupUnits(state), /absolute Linux/);
  }
  const units = backupUnits('/srv/chanter/production');
  assert.equal(Object.keys(units).length, 6);
  assert.match(units['chanter-backup-full.service'], /backup-runner.mjs \/srv\/chanter\/production full/);
  assert.match(units['chanter-backup-full.timer'], /Persistent=true/);
});

test('backup status rejects failures and marks overdue chains without copying provider metadata', () => {
  const now = Date.parse('2026-09-19T00:00:00Z');
  const complete = { error: false, type: 'full', label: '20260918-000000F', timestamp: { stop: now / 1000 - 3600 },
    database: { id: 1, 'repo-key': 1 } };
  const info = [{ name: 'chanter', status: { code: 0 }, db: [{ id: 1, 'repo-key': 1 }], backup: [complete], secret: 'do-not-copy' }];
  assert.equal(summarizeBackup(info, now).stale, false);
  assert.equal(JSON.stringify(summarizeBackup(info, now)).includes('do-not-copy'), false);
  assert.equal(summarizeBackup(info, now + 31 * 3600000).stale, true);
  assert.throws(() => summarizeBackup([{ ...info[0], status: { code: 1 } }], now), /unavailable/);
  assert.throws(() => summarizeBackup([{ ...info[0], backup: [{ ...complete, error: true }] }], now), /No complete/);
  assert.throws(() => summarizeBackup([{ ...info[0], db: [{ id: 2, 'repo-key': 1 }] }], now), /No complete/);
  assert.throws(() => summarizeBackup([{ ...info[0], backup: [{ ...complete, timestamp: { stop: now / 1000 + 3600 } }] }], now), /No complete/);
});

test('failed first migrations prevent an older writer even without a successful release', () => {
  const prior = { schemaEpoch: 5, commit: 'a'.repeat(40) };
  const attempted = { schemaEpoch: 6, commit: 'b'.repeat(40) };
  assert.doesNotThrow(() => assertMigrationFloor(prior, null));
  assert.throws(() => assertMigrationFloor(prior, attempted), /migration floor/);
  assert.doesNotThrow(() => assertMigrationFloor(attempted, attempted));
  assert.doesNotThrow(() => assertMigrationFloor({ ...attempted, schemaEpoch: 7 }, attempted));
  for (const invalid of [{}, { schemaEpoch: -1 }, { schemaEpoch: 6, commit: 'untrusted' }]) {
    assert.throws(() => assertMigrationFloor(attempted, invalid), /Invalid persisted/);
  }
});

test('production archive settings use encrypted TLS S3 with bounded workers', () => {
  const env = backupEnvironment(configured());
  assert.equal(env.PGBACKREST_REPO1_TYPE, 's3');
  assert.equal(env.PGBACKREST_REPO1_CIPHER_TYPE, 'aes-256-cbc');
  assert.equal(env.PGBACKREST_REPO1_STORAGE_VERIFY_TLS, 'y');
  assert.equal(env.PGBACKREST_REPO1_S3_ENDPOINT, 'backup.example.test');
  assert.equal(env.PGBACKREST_PROCESS_MAX, '1');
  assert.equal(env.PGBACKREST_REPO1_RETENTION_FULL, '2');
  assert.equal(env.PGBACKREST_REPO1_RETENTION_ARCHIVE, undefined);
});

test('incomplete credentials fail closed without echoing input', () => {
  for (const key of Object.keys(configured())) {
    const values = configured();
    delete values[key];
    assert.throws(() => backupEnvironment(values), error => {
      assert.equal(error.message.includes('fixture-'), false);
      return /required/.test(error.message);
    });
  }
});

test('backup endpoints reject downgrade, credentials, query and path injection', () => {
  for (const endpoint of ['http://backup.example.test', 'https://user:secret@backup.example.test',
    'https://backup.example.test/path', 'https://backup.example.test?secret=value',
    'https://backup.example.test/#fragment', 'https://backup.example.test\nPGBACKREST_REPO1_TYPE=posix']) {
    assert.throws(() => backupEnvironment({ ...configured(), CHANTER_BACKUP_S3_ENDPOINT: endpoint }),
      /HTTPS origin/);
  }
});

test('reject unsafe repository names and undersized encryption keys', () => {
  for (const bucket of ['../data', 'bucket\noption=value', 'a', 'UPPER_CASE']) {
    assert.throws(() => backupEnvironment({ ...configured(), CHANTER_BACKUP_S3_BUCKET: bucket }), /bucket/i);
  }
  assert.throws(() => backupEnvironment({ ...configured(), CHANTER_BACKUP_CIPHER_PASS: 'short' }), /32/);
});
