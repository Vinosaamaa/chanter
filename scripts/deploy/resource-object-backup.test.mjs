import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { ResourceObjectArchive, resourceBackupEnvironment, MAX_RESOURCE_BYTES } from './resource-object-backup.mjs';

const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const courseId = '11111111-1111-4111-8111-111111111111';
const resourceId = '22222222-2222-4222-8222-222222222222';
const objectId = '33333333-3333-4333-8333-333333333333';
const content = Buffer.from([0, 255, 128, 1, 13, 10, 195, 40]);
const entry = bytes => ({ resourceId, courseId, key: `resources/v1/${courseId}/${resourceId}/${objectId}`,
  byteSize: bytes.length, sha256: hash(bytes), providerVersionId: null, disposition: 'EXTANT', storageWriteSettled: true });
const maintenance = { inventoryId: '44444444-4444-4444-8444-444444444444', storageNamespaceSha256: 'a'.repeat(64),
  writers: 'QUIESCENT', unsettledWrites: 0, authority: { revision: 0, digest: '0'.repeat(64) } };
const settings = { CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test', CHANTER_BACKUP_S3_BUCKET: 'fixture-backups',
  CHANTER_BACKUP_S3_REGION: 'fixture', CHANTER_BACKUP_S3_ACCESS_KEY: 'private-access-canary',
  CHANTER_BACKUP_S3_SECRET_KEY: 'private-secret-canary', CHANTER_BACKUP_CIPHER_PASS: 'd'.repeat(64),
  CHANTER_CONFIG_BACKUP_PASSWORD: 'c'.repeat(64), CHANTER_TERMINAL_JOURNAL_PASSWORD: 'j'.repeat(64),
  CHANTER_RESOURCE_BACKUP_PASSWORD: 'r'.repeat(64) };

function bundle(binary = null) {
  const parent = path.resolve('.cache/resource-object-tests'); fs.mkdirSync(parent, { recursive: true });
  const root = fs.mkdtempSync(path.join(parent, 'run-')); fs.mkdirSync(path.join(root, 'tools'));
  const name = process.platform === 'win32' ? 'restic.exe' : 'restic';
  const bytes = binary ? fs.readFileSync(binary) : Buffer.from('fixture binary');
  fs.writeFileSync(path.join(root, 'tools', name), bytes, { mode: 0o700 });
  fs.writeFileSync(path.join(root, 'tools/restic.sha256'), `${hash(bytes)}  ${name}`);
  return root;
}

test('object repository uses a separate encrypted prefix and rejects missing or reused keys', () => {
  const env = resourceBackupEnvironment(settings, 'production');
  assert.equal(env.RESTIC_REPOSITORY, 's3:https://backup.example.test/fixture-backups/resource-objects/production');
  assert.equal(env.GOMAXPROCS, '1'); assert.equal(env.GOMEMLIMIT, '256MiB');
  for (const password of ['', 'short', settings.CHANTER_BACKUP_CIPHER_PASS, settings.CHANTER_CONFIG_BACKUP_PASSWORD,
    settings.CHANTER_TERMINAL_JOURNAL_PASSWORD, 'r'.repeat(32) + '\n'])
    assert.throws(() => resourceBackupEnvironment({ ...settings, CHANTER_RESOURCE_BACKUP_PASSWORD: password }, 'production'));
});

test('capture requires canonical settled extant bytes and quiescent maintenance before storage writes', () => {
  let calls = 0;
  const archive = new ResourceObjectArchive({ bundleDir: bundle(), environment: 'staging',
    env: resourceBackupEnvironment(settings, 'staging'), execute: () => { calls++; throw Error('must not execute'); } });
  for (const change of [{ providerVersionId: 'invented' }, { storageWriteSettled: false }, { disposition: 'DELETED' },
    { disposition: 'UNKNOWN' }, { key: '../outside' }, { resourceId: objectId }, { byteSize: 0 },
    { byteSize: MAX_RESOURCE_BYTES + 1 }, { sha256: 'f'.repeat(64) }, { unreviewed: true }])
    assert.throws(() => archive.capture({ ...entry(content), ...change }, maintenance, content));
  for (const change of [{ writers: 'RUNNING' }, { unsettledWrites: 1 }, { storageNamespaceSha256: '' }, { inventoryId: '' }])
    assert.throws(() => archive.capture(entry(content), { ...maintenance, ...change }, content));
  assert.throws(() => archive.capture(entry(content), maintenance, Buffer.alloc(MAX_RESOURCE_BYTES + 1)));
  assert.equal(calls, 0);
});

test('binary capture verifies full immutable read-back before returning a private reference', () => {
  const calls = []; let returned = content, error = false;
  const archive = new ResourceObjectArchive({ bundleDir: bundle(), environment: 'staging',
    env: resourceBackupEnvironment(settings, 'staging'), execute: (_file, args, options) => {
      calls.push({ args, options });
      assert.equal(JSON.stringify(args).includes(resourceId), false);
      assert.equal(JSON.stringify(args).includes('canary'), false);
      assert.equal(options.env.AWS_SECRET_ACCESS_KEY, settings.CHANTER_BACKUP_S3_SECRET_KEY);
      assert.equal(options.windowsHide, true); assert.ok(options.timeout <= 60_000);
      if (error) throw Error('private-provider-canary');
      if (args.includes('backup')) {
        assert.ok(Buffer.isBuffer(options.input)); assert.deepEqual(options.input, content);
        return Buffer.from(JSON.stringify({ message_type: 'summary', snapshot_id: 'b'.repeat(64) }));
      }
      assert.ok(args.includes('dump')); assert.ok(options.maxBuffer <= content.length + 1);
      return returned;
    } });
  const reference = archive.capture(entry(content), maintenance, content);
  assert.deepEqual(calls.map(call => call.args[1]), ['backup', 'dump']);
  assert.equal(reference.snapshotId, 'b'.repeat(64));
  assert.equal(reference.inventoryId, maintenance.inventoryId);
  assert.equal(reference.storageNamespaceSha256, maintenance.storageNamespaceSha256);
  assert.equal(reference.object.providerVersionId, null);
  assert.equal(reference.publicCutoverAllowed, false);
  assert.deepEqual(archive.readVerified(reference, entry(content), maintenance.storageNamespaceSha256), content);
  const readCount = calls.length;
  assert.throws(() => archive.readVerified(reference, { ...entry(content), disposition: 'DELETED' }, maintenance.storageNamespaceSha256));
  assert.throws(() => archive.readVerified(reference, entry(content), 'c'.repeat(64)));
  assert.throws(() => archive.readVerified({ ...reference, snapshotId: 'latest' }, entry(content), maintenance.storageNamespaceSha256));
  assert.equal(calls.length, readCount);
  for (const bytes of [Buffer.from('abcdefgh'), content.subarray(0, -1), Buffer.concat([content, Buffer.from([0])])]) {
    returned = bytes;
    assert.throws(() => archive.capture(entry(content), maintenance, content), /Resource object archive verification failed/);
  }
  error = true;
  assert.throws(() => archive.readVerified(reference, entry(content), maintenance.storageNamespaceSha256),
    /^Error: Resource object archive verification failed$/);
});

test('actual restic encrypts binary objects and refuses substituted bytes and the wrong key',
  { skip: !process.env.CHANTER_TEST_RESTIC }, () => {
    const bundleDir = bundle(process.env.CHANTER_TEST_RESTIC);
    const env = { RESTIC_REPOSITORY: path.join(bundleDir, 'repository'), RESTIC_PASSWORD: 'fixture-resource-secret-'.repeat(3),
      GOMAXPROCS: '1', GOMEMLIMIT: '256MiB', TEMP: bundleDir, TMP: bundleDir, SystemRoot: process.env.SystemRoot ?? '' };
    const archive = new ResourceObjectArchive({ bundleDir, environment: 'staging', kind: 'fixture', env });
    archive.initialize();
    const canary = Buffer.from('private-resource-bytes-canary-332');
    const bytes = Buffer.concat([canary, crypto.randomBytes(64 * 1024), content]);
    const reference = archive.capture(entry(bytes), maintenance, bytes);
    assert.deepEqual(archive.readVerified(reference, entry(bytes), maintenance.storageNamespaceSha256), bytes);
    const changed = Buffer.from(bytes); changed[changed.length - 1] ^= 1;
    const changedReference = archive.capture(entry(changed), maintenance, changed);
    assert.throws(() => archive.readVerified({ ...reference, snapshotId: changedReference.snapshotId }, entry(bytes),
      maintenance.storageNamespaceSha256), /^Error: Resource object archive verification failed$/);
    const walk = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(item =>
      item.isDirectory() ? walk(path.join(directory, item.name)) : [path.join(directory, item.name)]);
    for (const file of walk(env.RESTIC_REPOSITORY)) assert.equal(fs.readFileSync(file).includes(canary), false);
    const wrong = new ResourceObjectArchive({ bundleDir, environment: 'staging', kind: 'fixture', env: { ...env, RESTIC_PASSWORD: 'wrong-key' } });
    assert.throws(() => wrong.readVerified(reference, entry(bytes), maintenance.storageNamespaceSha256),
      /^Error: Resource object archive verification failed$/);
  });
