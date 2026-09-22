import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { GENESIS, entryDigest } from './terminal-journal.mjs';
import { replicateJournal } from './terminal-journal-replica.mjs';
import { recoverCurrentAuthority, SOURCES } from './terminal-journal-recovery.mjs';
import { scopeStartDigest, recoveryScopeBasis, START } from './deleted-scope.mjs';

const recoveryId = '33333333-3333-4333-8333-333333333333';
const restoreId = '44444444-4444-4444-8444-444444444444';
async function fixture(targetKind = 'ACCOUNT', derivedMode = 'normal') {
  const entry = { revision: 1, eventId: '11111111-1111-4111-8111-111111111111', targetKind,
    targetId: '22222222-2222-4222-8222-222222222222', action: 'DELETE', retentionPolicy: 'PRESERVE_MODERATION_RECORDS_V1', deletedAt: '2026-09-19T06:00:00Z', previousDigest: GENESIS.digest };
  entry.digest = entryDigest(entry);
  const originalIds = ['00000000-0000-0000-0000-000000000001'];
  const scope = (kind, after = START, derived = false) => {
    const original = kind === 'COURSE' ? originalIds : [];
    const ids = derived && kind === 'COURSE' ? derivedMode === 'omit' ? ['00000000-0000-0000-0000-000000000002']
      : [...original, '00000000-0000-0000-0000-000000000002'] : original;
    const digest = (basis, values) => values.reduce((previous, id) => createHash('sha256').update(`${previous}\n${id}\n`).digest('hex'),
      scopeStartDigest(basis, kind, values.length));
    const originalScopeDigest = digest(entry.digest, original);
    const basis = derived ? recoveryScopeBasis(restoreId, entry, kind, originalScopeDigest) : entry.digest;
    return { schemaVersion: 1, studyServerId: entry.targetId, terminalRevision: entry.revision,
      terminalEventId: entry.eventId, terminalDigest: entry.digest, kind, after, totalCount: ids.length,
      scopeDigest: digest(basis, ids), ids, nextAfter: null };
  };
  const ready = page => {
    const value = { ...page, after: page.ids.at(-1) ?? START, receivedCount: page.totalCount, ready: true };
    delete value.ids; delete value.nextAfter; return value;
  };
  const envelope = (kind, operationId, field, value) => ({ schemaVersion: 1, restoreId, recoveryId: operationId,
    originalScopeDigest: scope(kind).scopeDigest, [field]: value });
  const authority = { revision: 1, digest: entry.digest }, objects = new Map(), listing = [], calls = [];
  const repository = { kind: 'fixture', environment: 'staging', manifests: () => structuredClone(listing),
    read: (kind, id) => structuredClone(objects.get(id)), write: (kind, value) => {
      const id = createHash('sha256').update(JSON.stringify(value)).digest('hex'); objects.set(id, structuredClone(value));
      if (kind === 'manifest') listing.push({ snapshotId: id, authority: value.authority }); return id;
    } };
  await replicateJournal({ kind: 'fixture', checkpoint: async () => null, acknowledge: async value => value,
    page: async () => ({ schemaVersion: 2, after: GENESIS, through: authority, entries: [entry], next: authority }) }, repository,
  { kind: 'fixture', scope: async (entry, kind, after) => scope(kind, after) });
  const clients = Object.fromEntries(SOURCES.map(source => {
    let current = GENESIS;
    return [source, { kind: 'fixture', reapply: async page => { current = page.next; calls.push(`${source}:reapply`); },
      receipt: async () => ({ schemaVersion: 1, source, authority: current, pendingTargets: current.revision, preservedTargets: 0 }),
      importScope: async ({ page }) => {
        calls.push(`${source}:scope:${page.kind}`);
        return ready(page);
      },
      deriveScope: async request => { calls.push(`${source}:derive`); return ['COURSE', 'CHANNEL'].map(kind =>
        envelope(kind, request.recoveryId, 'scope', ready(scope(kind, START, true)))); },
      recoveryScope: async request => envelope(request.kind, request.recoveryId, 'page', scope(request.kind, request.after, true)),
      importRecoveryScope: async request => { calls.push(`${source}:derived:${request.page.kind}`);
        return envelope(request.page.kind, request.recoveryId, 'scope', ready(request.page)); },
      invalidate: async request => ({ schemaVersion: 1, source, ...request, invalidatedAt: '2026-09-22T00:00:00Z',
        scope: source === 'auth' ? 'ALL_BROWSER_SESSIONS' : 'ALL_PENDING_NATIVE_REQUESTS' }) }];
  }));
  return { repository, clients, authority, calls, listing, restoreId };
}

test('recovery requires all actual participant receipts and both invalidations, but never opens ingress', async () => {
  const f = await fixture();
  const input = { ...f, recoveryId, requiredAuthority: f.authority };
  const result = await recoverCurrentAuthority(input);
  assert.equal(result.publicCutoverAllowed, false);
  assert.equal(result.status, 'authority-receipts-verified');
  assert.equal(result.isolationVerified, false);
  assert.equal(result.participants.length, 7);
  assert.equal(result.invalidations.length, 2);
  assert.ok(result.participants.every(receipt => receipt.pendingTargets === 1));
  assert.equal(JSON.stringify(result).includes('22222222-2222'), false);
  assert.deepEqual(await recoverCurrentAuthority(input), result, 'Stable retry preserves source-owned invalidation receipts');
});

test('current archived scope stages before community replay and follows dependent fences, including notification', async () => {
  const f = await fixture('STUDY_SERVER');
  await recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority });
  const index = call => f.calls.indexOf(call);
  assert.ok(index('auth:reapply') < index('community:scope:COURSE'));
  assert.ok(index('community:scope:CHANNEL') < index('community:reapply'));
  for (const source of SOURCES.filter(value => !['auth', 'community'].includes(value))) {
    for (const kind of ['COURSE', 'CHANNEL']) assert.ok(index(`${source}:reapply`) < index(`${source}:scope:${kind}`));
  }
  assert.equal(f.calls.filter(call => call.includes(':scope:')).length, 12);
  assert.equal(f.calls.some(call => call.startsWith('auth:scope:')), false);
});

test('unready or mismatched scope receipt blocks session invalidation and final authority receipt', async () => {
  for (const source of SOURCES.filter(value => value !== 'auth')) {
    const f = await fixture('STUDY_SERVER'), original = f.clients[source].importScope;
    f.clients[source].importScope = async request => ({ ...await original(request), ready: false });
    let invalidations = 0;
    f.clients.auth.invalidate = async () => { invalidations++; throw Error('Unexpected invalidation'); };
    await assert.rejects(recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority }));
    assert.equal(invalidations, 0);
  }
});

test('historical union is restore-bound, includes every original ID and follows both current kind imports', async () => {
  const f = await fixture('STUDY_SERVER');
  const before = JSON.stringify(f.repository.manifests());
  await recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority });
  assert.ok(f.calls.indexOf('community:derive') > f.calls.indexOf('notification:scope:CHANNEL'));
  for (const source of ['message', 'media', 'agent', 'search', 'notification']) for (const kind of ['COURSE', 'CHANNEL'])
    assert.ok(f.calls.indexOf(`${source}:derived:${kind}`) > f.calls.indexOf('community:derive'));
  assert.equal(JSON.stringify(f.repository.manifests()), before);
  await recoverCurrentAuthority({ ...f, recoveryId: '55555555-5555-4555-8555-555555555555', requiredAuthority: f.authority });
  const omitted = await fixture('STUDY_SERVER', 'omit');
  await assert.rejects(recoverCurrentAuthority({ ...omitted, recoveryId, requiredAuthority: omitted.authority }));
});

test('missing restore identity, wrong derived authority or unready derived import cannot invalidate sessions', async () => {
  for (const mode of ['missing', 'identity', 'unready']) {
    const f = await fixture('STUDY_SERVER'); let invalidations = 0;
    if (mode === 'missing') delete f.restoreId;
    if (mode === 'identity') {
      const read = f.clients.community.recoveryScope;
      f.clients.community.recoveryScope = async request => ({ ...await read(request), restoreId: recoveryId });
    }
    if (mode === 'unready') {
      const write = f.clients.media.importRecoveryScope;
      f.clients.media.importRecoveryScope = async request => { const response = await write(request); response.scope.ready = false; return response; };
    }
    f.clients.auth.invalidate = async () => { invalidations++; throw Error('Unexpected invalidation'); };
    await assert.rejects(recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority }));
    assert.equal(invalidations, 0);
  }
});

test('each omitted, failed or stale source and wrong invalidation prevents a completion receipt', async () => {
  for (const source of SOURCES) {
    for (const mode of ['missing', 'failure', 'stale']) {
      const f = await fixture();
      if (mode === 'missing') delete f.clients[source];
      if (mode === 'failure') f.clients[source].reapply = async () => { throw Error('private-source-body'); };
      if (mode === 'stale') f.clients[source].receipt = async () => ({ schemaVersion: 1, source, authority: GENESIS, pendingTargets: 0, preservedTargets: 0 });
      await assert.rejects(recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority }));
    }
  }
  for (const source of ['auth', 'agent']) {
    const f = await fixture();
    const invalidate = f.clients[source].invalidate;
    f.clients[source].invalidate = async request => ({ ...await invalidate(request), authority: GENESIS });
    await assert.rejects(recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority }));
  }
});

test('an advancing external prefix during replay or impossible cleanup counts fails closed', async () => {
  for (const mode of ['head', 'count']) {
    const f = await fixture();
    if (mode === 'head') f.clients.search.reapply = async () => {
      f.listing.push({ snapshotId: 'a'.repeat(64), authority: { revision: 2, digest: 'b'.repeat(64) } });
    };
    else f.clients.search.receipt = async () => ({ schemaVersion: 1, source: 'search', authority: f.authority, pendingTargets: 2, preservedTargets: 0 });
    await assert.rejects(recoverCurrentAuthority({ ...f, recoveryId, requiredAuthority: f.authority }));
  }
});
