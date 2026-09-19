import test from 'node:test';
import assert from 'node:assert/strict';
import { backupEnvironment } from './recovery.mjs';

const configured = () => ({
  CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test',
  CHANTER_BACKUP_S3_BUCKET: 'chanter-backups',
  CHANTER_BACKUP_S3_REGION: 'region-1',
  CHANTER_BACKUP_S3_ACCESS_KEY: 'fixture-access',
  CHANTER_BACKUP_S3_SECRET_KEY: 'fixture-secret',
  CHANTER_BACKUP_CIPHER_PASS: 'fixture-independent-backup-passphrase-32bytes',
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
