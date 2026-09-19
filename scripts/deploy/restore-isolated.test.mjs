import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { restoreIsolated, selectRestoreBackup } from './restore-isolated.mjs';
import { imageNames } from './release.mjs';

const release = { version: 1, commit: 'a'.repeat(40), schemaEpoch: 7, architecture: 'amd64',
  images: Object.fromEntries(imageNames.map(name => [name, 'sha256:' + 'b'.repeat(64)])) };
const label = '20260101-000000F';
const targetTime = '2026-01-01T01:00:00.000Z';
const info = [{ name: 'chanter', status: { code: 0 }, db: [{ id: 1, 'repo-key': 1 }], backup: [
  { label, type: 'full', error: false, database: { id: 1, 'repo-key': 1 }, timestamp: { stop: Date.parse('2026-01-01T00:01:00Z') / 1000 },
    annotation: { release: release.commit, 'config-snapshot': 'c'.repeat(64) } }] }];
const settings = { CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test', CHANTER_BACKUP_S3_BUCKET: 'backup-fixture',
  CHANTER_BACKUP_S3_REGION: 'fixture-region', CHANTER_BACKUP_S3_ACCESS_KEY: 'private-fixture-key',
  CHANTER_BACKUP_S3_SECRET_KEY: 'private-fixture-secret', CHANTER_BACKUP_CIPHER_PASS: 'd'.repeat(64),
  CHANTER_CONFIG_BACKUP_PASSWORD: 'e'.repeat(64) };
function fixture(t) {
  const parent = path.resolve('.cache/isolated-restore-tests');
  fs.mkdirSync(parent, { recursive: true });
  const root = fs.mkdtempSync(path.join(parent, 'run-'));
  t.after(() => { assert.equal(path.dirname(fs.realpathSync(root)), fs.realpathSync(parent)); fs.rmSync(root, { recursive: true }); });
  const bundleDir = path.join(root, 'bundle');
  fs.mkdirSync(bundleDir); fs.writeFileSync(path.join(bundleDir, 'release.json'), JSON.stringify(release));
  const options = { bundleDir, settings, environment: 'staging', destination: path.join(root, 'restored'), label, targetTime };
  const calls = [];
  const run = args => {
    calls.push(args);
    if (args.at(-1) === 'info') return JSON.stringify(info);
    if (args[0] === 'exec') return 't\n';
    return '';
  };
  return { options, calls, run };
}

test('operator must select an existing chain and its exact release after backup completion', () => {
  assert.equal(selectRestoreBackup(info, label, targetTime, release).configSnapshot, 'c'.repeat(64));
  for (const target of ['2026-01-01T00:00:00.000Z', '2999-01-01T01:00:00.000Z', 'not-a-date']) {
    assert.throws(() => selectRestoreBackup(info, label, target, release), /Recovery target/);
  }
  assert.throws(() => selectRestoreBackup(info, label, targetTime, { ...release, commit: 'b'.repeat(40) }), /matching/);
  assert.throws(() => selectRestoreBackup(info, '--set=untrusted', targetTime, release), /label/);
});

test('recovery rejects existing destinations and active environments before creating state', async t => {
  const { options, run, calls } = fixture(t);
  fs.mkdirSync(options.destination);
  await assert.rejects(restoreIsolated(options, run), /already exists/);
  assert.equal(calls.length, 0);
  fs.rmdirSync(options.destination);
  await assert.rejects(restoreIsolated(options, () => 'running-container'), /separate recovery host/);
  assert.equal(fs.existsSync(options.destination), false);
});

test('restore validates encrypted configuration before new volumes and never authorizes public cutover', async t => {
  const { options, run, calls } = fixture(t);
  let checked = false;
  const verify = (_, __, environment, snapshotId, commit) => {
    assert.equal(environment, 'staging'); assert.equal(snapshotId, 'c'.repeat(64)); assert.equal(commit, release.commit);
    assert.equal(calls.some(args => args[0] === 'volume' && args[1] === 'create'), false);
    checked = true;
  };
  const result = await restoreIsolated(options, run, verify);
  assert.equal(checked, true); assert.equal(result.status, 'database-restored-isolated');
  assert.equal(result.publicCutoverAllowed, false);
  assert.ok(result.pending.includes('current-deletion-journal'));
  assert.equal(calls.some(args => args.includes('-p') || args.includes('--publish')), false);
  assert.equal(JSON.stringify(calls).includes('private-fixture-secret'), false);
  const server = calls.find(args => args.includes('--entrypoint'));
  assert.ok(server.includes('archive_mode=off')); assert.ok(server.includes('listen_addresses='));
  assert.equal(server.some(value => value.startsWith('max_connections=')), false,
    'WAL replay must not lower connection capacity below the recorded primary setting');
  assert.deepEqual(calls.at(-1), ['network', 'disconnect', result.network, result.container]);
});

test('a failed restore stops its own database and preserves its named state for review', async t => {
  const { options, run, calls } = fixture(t);
  await assert.rejects(restoreIsolated(options, args => {
    if (args.at(-1) === 'restore') throw new Error('private-repository-failure');
    return run(args);
  }, () => {}), /^Error: Isolated recovery failed/);
  const receipt = JSON.parse(fs.readFileSync(path.join(options.destination, 'recovery.json')));
  assert.equal(receipt.status, 'failed-preserved');
  assert.equal(calls.some(args => args[0] === 'stop'), false, 'no container was created before this failure');
  assert.equal(calls.some(args => args.includes('rm') && args[0] !== 'run'), false);
  assert.equal(JSON.stringify(receipt).includes('private-repository-failure'), false);
  await assert.rejects(restoreIsolated(options, run), /already exists/);
});
