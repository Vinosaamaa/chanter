import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { imageNames } from './release.mjs';
import { applyRecoveryAuthority, verifyRestoredDatabase } from './restore-current-authority.mjs';
import { GENESIS, checkpointIdentity } from './terminal-journal.mjs';
import { SOURCES } from './terminal-journal-recovery.mjs';

const id = 'chanter-recovery-11111111-1111-4111-8111-111111111111';
const originalContainerId = 'e'.repeat(64);
const release = { version: 1, commit: 'a'.repeat(40), architecture: process.arch === 'arm64' ? 'arm64' : 'amd64', schemaEpoch: 8,
  recoveryProtocol: { journalSchema: 2, ordinaryWorkIsolation: 1 },
  images: Object.fromEntries(imageNames.map(name => [name, 'sha256:' + 'b'.repeat(64)])) };
const receipt = { version: 1, status: 'database-restored-isolated', release: release.commit, image: release.images.postgres,
  container: id, volume: `${id}-data`, network: `${id}-network`, configSnapshot: 'c'.repeat(64), publicCutoverAllowed: false };

function files() {
  const parent = path.resolve('.cache/current-authority-tests'); fs.mkdirSync(parent, { recursive: true });
  const root = fs.mkdtempSync(path.join(parent, 'run-')), bundleDir = path.join(root, 'bundle'), destination = path.join(root, 'restored');
  fs.mkdirSync(bundleDir); fs.mkdirSync(destination);
  fs.writeFileSync(path.join(bundleDir, 'release.json'), JSON.stringify(release));
  fs.writeFileSync(path.join(destination, 'recovery.json'), JSON.stringify(receipt));
  return { bundleDir, destination, environment: 'staging', requiredAuthority: GENESIS, settings: {} };
}

test('unsupported historical release rejects before decryption, Docker or source startup', async () => {
  const input = files();
  fs.writeFileSync(path.join(input.bundleDir, 'release.json'), JSON.stringify({ ...release, recoveryProtocol: undefined }));
  let operations = 0;
  await assert.rejects(applyRecoveryAuthority(input, { run: () => { operations++; }, loadConfiguration: () => { operations++; } }), /capability/);
  assert.equal(operations, 0);
  assert.equal(fs.existsSync(path.join(input.destination, 'authority')), false);
});

test('foreign architecture rejects before decryption or touching restored state', async () => {
  const input = files();
  fs.writeFileSync(path.join(input.bundleDir, 'release.json'), JSON.stringify({ ...release,
    architecture: release.architecture === 'amd64' ? 'arm64' : 'amd64' }));
  let operations = 0;
  await assert.rejects(applyRecoveryAuthority(input, { run: () => { operations++; }, loadConfiguration: () => { operations++; } }));
  assert.equal(operations, 0);
  assert.equal(fs.existsSync(path.join(input.destination, '.authority-lock')), false);
});

test('restored volume ownership, image and network isolation are checked before stopping the database', () => {
  const valid = { id: originalContainerId, image: receipt.image, labels: { 'chanter.recovery': id }, networks: {}, ports: {},
    mounts: [{ Type: 'volume', Name: receipt.volume, Destination: '/var/lib/postgresql/data' }] };
  const calls = [];
  const run = args => { calls.push(args); return JSON.stringify(args[0] === 'volume' ? { 'chanter.recovery': id } : valid); };
  assert.equal(verifyRestoredDatabase(receipt, run), originalContainerId);
  assert.ok(calls.every(args => args.includes('inspect')));
  for (const changed of [{ image: 'sha256:' + 'd'.repeat(64) }, { networks: { public: {} } },
    { labels: {} }, { ports: { '5432/tcp': [{}] } }, { mounts: [] },
    { mounts: [...valid.mounts, { Type: 'bind', Source: '/foreign', Destination: '/foreign' }] }]) {
    assert.throws(() => verifyRestoredDatabase(receipt, args => JSON.stringify(args[0] === 'volume' ? { 'chanter.recovery': id } : { ...valid, ...changed })));
  }
});

test('unavailable or mismatched encrypted configuration never reaches a Docker mutation', async () => {
  for (const mode of ['unavailable', 'foreign']) {
    const input = files(), calls = [];
    await assert.rejects(applyRecoveryAuthority(input, { run: args => { calls.push(args); return ''; },
      loadConfiguration: () => { if (mode === 'unavailable') throw Error('private-provider-canary'); return { release: { ...release, commit: 'd'.repeat(40) } }; } }));
    assert.equal(calls.length, 0);
    assert.equal(fs.existsSync(path.join(input.destination, 'authority')), false);
  }
});

test('authority application preserves attempt identity, private configuration and stopped owned containers', async () => {
  const input = files();
  input.settings = { CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test', CHANTER_BACKUP_S3_BUCKET: 'fixture',
    CHANTER_BACKUP_S3_REGION: 'fixture', CHANTER_BACKUP_S3_ACCESS_KEY: 'fixture', CHANTER_BACKUP_S3_SECRET_KEY: 'fixture',
    CHANTER_BACKUP_CIPHER_PASS: 'b'.repeat(64), CHANTER_CONFIG_BACKUP_PASSWORD: 'c'.repeat(64), CHANTER_TERMINAL_JOURNAL_PASSWORD: 'j'.repeat(64) };
  const config = { environment: 'staging', hostname: 'staging.chanter.example', publicIp: '192.0.2.1' };
  const names = ['postgres', ...SOURCES.map(source => `${source}-service`)];
  const snapshot = { version: 1, release, config,
    runtime: Object.fromEntries(names.map(name => [name, { PRIVATE_FIXTURE: 'private-recovery-canary' }])) };
  const project = 'chanter-recovery-11111111111141118111111111111111', network = `${id}-authority`;
  const containers = new Map(), calls = [];
  let rejectSource = false, isolationResult = 'RECOVERY_SOURCE_LISTENERS_PRIVATE', containerTamper = null, schemaFailure = false;
  let originalNameReplaced = false, networkTamper = null, sourceClientCalls = 0, replaceNetworkAfterPin = false, networkReads = 0;
  const networkId = 'f'.repeat(64);
  const inspect = source => ({ image: release.images[source], labels: { 'chanter.recovery': id,
    'com.docker.compose.project': project, 'com.docker.compose.service': source }, networks: { [network]: { NetworkID: networkId } }, ports: {},
    mounts: source === 'postgres' ? [{ Type: 'volume', Name: receipt.volume, Destination: '/var/lib/postgresql/data' }] : [] });
  const run = args => {
    calls.push(args);
    if (args[0] === 'stop' && args[1] === originalContainerId && originalNameReplaced)
      throw Error('The inspected container disappeared; its name now refers to another ID');
    if (args[0] === 'volume') return JSON.stringify({ 'chanter.recovery': id });
    if (args[0] === 'inspect') return JSON.stringify(args.at(-1) === id
      ? { ...inspect('postgres'), id: originalContainerId, networks: {} } : { ...inspect(containers.get(args.at(-1))), ...containerTamper });
    if (args[0] === 'ps') return args.includes(`label=com.docker.compose.project=${project}`) ? [...containers.keys()].join('\n') : '';
    if (args[0] === 'network') {
      if (args[1] !== 'inspect') return '';
      networkReads++;
      return JSON.stringify({ id: replaceNetworkAfterPin && networkReads > 1 ? '0'.repeat(64) : networkId,
        name: network, internal: true, labels: { 'chanter.recovery': id,
          'com.docker.compose.project': project, 'com.docker.compose.network': 'application' }, ...networkTamper });
    }
    if (args[0] === 'compose' && args.includes('up')) containers.set(crypto.createHash('sha256').update(args.at(-1)).digest('hex').slice(0, 12), args.at(-1));
    if (args.includes('RecoverySchema')) {
      if (schemaFailure && args.includes('media-service')) throw Error('Restored checksum mismatch');
      return 'RECOVERY_SCHEMA_VERIFIED';
    }
    if (args.includes('RecoveryIsolation')) {
      if (isolationResult instanceof Error) throw isolationResult;
      return isolationResult;
    }
    return '';
  };
  const checkpointId = checkpointIdentity('staging', GENESIS);
  const repository = { kind: 'fixture', environment: 'staging', manifests: () => [{ snapshotId: 'd'.repeat(64), authority: GENESIS }],
    read: () => ({ schemaVersion: 1, environment: 'staging', checkpointId, authority: GENESIS, createdAt: '2026-09-22T00:00:00.000Z', pages: [] }) };
  const options = { run, loadConfiguration: () => snapshot, repositoryFactory: () => repository,
    clientFactory: ({ source }) => { sourceClientCalls++; return ({ kind: 'fixture', checkpoint: async () => null,
      receipt: async () => ({ schemaVersion: 1, source, authority: GENESIS, pendingTargets: 0, preservedTargets: 0 }),
      reapply: async () => { if (rejectSource && source === 'message') throw Error('private-failure-canary'); },
      invalidate: async request => ({ schemaVersion: 1, source, ...request, invalidatedAt: '2026-09-22T00:00:00Z',
        scope: source === 'auth' ? 'ALL_BROWSER_SESSIONS' : 'ALL_PENDING_NATIVE_REQUESTS' }) }); } };
  const result = await applyRecoveryAuthority(input, options);
  assert.equal(result.publicCutoverAllowed, false);
  assert.equal(result.isolationVerified, true);
  assert.equal(result.status, 'current-authority-applied-isolated');
  assert.ok(calls.some(args => args[0] === 'stop' && args[1] === originalContainerId));
  assert.equal(calls.some(args => args[0] === 'stop' && args[1] === id), false);
  assert.equal(JSON.stringify(result).includes('private-recovery-canary'), false);
  assert.equal(JSON.stringify(calls).includes('private-recovery-canary'), false);
  assert.equal(calls.filter(args => args.includes('RecoverySchema')).length, 7);
  const firstSourceStart = calls.findIndex(args => args.includes('up') && args.at(-1) === 'auth-service');
  assert.equal(calls.slice(0, firstSourceStart).filter(args => args.includes('RecoverySchema')).length, 7,
    'Every restored source schema must validate before the first source starts');
  assert.equal(calls.at(-1)[0], 'stop');
  assert.deepEqual(await applyRecoveryAuthority(input, options), result);
  rejectSource = true;
  await assert.rejects(applyRecoveryAuthority(input, options), error => !error.message.includes('canary'));
  const attempt = JSON.parse(fs.readFileSync(path.join(input.destination, `authority-${checkpointId}`, 'attempt.json')));
  assert.equal(attempt.status, 'failed-preserved');
  assert.equal(attempt.recoveryId, result.recoveryId);
  assert.equal(calls.at(-1)[0], 'stop');
  assert.equal(fs.existsSync(path.join(input.destination, '.authority-lock')), false);
  rejectSource = false;
  for (const result of ['', new Error('Docker execution failed')]) {
    isolationResult = result;
    await assert.rejects(applyRecoveryAuthority(input, options));
    assert.equal(calls.at(-1)[0], 'stop');
  }
  isolationResult = 'RECOVERY_SOURCE_LISTENERS_PRIVATE';
  for (const tamper of [{ image: 'sha256:' + 'f'.repeat(64) }, { labels: {} },
    { networks: { public: {} } }, { ports: { '8080/tcp': [{}] } },
    { mounts: [{ Type: 'bind', Source: '/foreign', Destination: '/foreign' }] }]) {
    containerTamper = tamper; calls.length = 0;
    await assert.rejects(applyRecoveryAuthority(input, options));
    assert.equal(calls.some(args => args[0] === 'stop' || args.includes('up')), false,
      'A foreign or exposed container must prevent every stop/start operation');
  }
  containerTamper = null; schemaFailure = true; calls.length = 0;
  await assert.rejects(applyRecoveryAuthority(input, options));
  assert.equal(calls.filter(args => args.includes('up') && args.at(-1) !== 'postgres').length, 0,
    'One invalid restored schema must prevent every source application from starting');
  schemaFailure = false;
  for (const tamper of [{ labels: {} }, { internal: false }, { name: 'foreign' }, { id: '0'.repeat(64) }]) {
    networkTamper = tamper; sourceClientCalls = 0;
    await assert.rejects(applyRecoveryAuthority(input, options));
    assert.equal(sourceClientCalls, 0, 'A substituted network cannot reach participant replay');
  }
  networkTamper = null; replaceNetworkAfterPin = true; networkReads = 0; sourceClientCalls = 0;
  await assert.rejects(applyRecoveryAuthority(input, options));
  assert.equal(sourceClientCalls, 0, 'A replacement retaining the labels still fails the pinned network identity');
  replaceNetworkAfterPin = false; originalNameReplaced = true; calls.length = 0;
  await assert.rejects(applyRecoveryAuthority(input, options));
  assert.equal(calls.some(args => args.includes('up')), false,
    'Disappearance of the pinned container must stop recovery even if its name was replaced');
  assert.equal(calls.some(args => args[0] === 'stop' && args[1] === id), false);
});
