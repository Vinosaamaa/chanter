import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { GENESIS, entryDigest } from './terminal-journal.mjs';
import { replicateJournal, readCurrentReplica } from './terminal-journal-replica.mjs';

function fixture(count = 2) {
  const entries = [];
  for (let revision = 1; revision <= count; revision++) {
    const id = revision.toString(16).padStart(12, '0');
    const entry = { revision, eventId: `11111111-1111-4111-8111-${id}`, targetKind: 'ACCOUNT',
      targetId: `22222222-2222-4222-8222-${id}`, action: 'DELETE', deletedAt: '2026-09-19T06:00:00Z',
      previousDigest: entries.at(-1)?.digest ?? GENESIS.digest };
    entry.digest = entryDigest(entry); entries.push(entry);
  }
  const mark = revision => revision === 0 ? GENESIS : { revision, digest: entries[revision - 1].digest };
  const objects = new Map(), manifests = [], calls = [];
  let checkpoint = null;
  const source = { kind: 'fixture', checkpoint: async () => checkpoint,
    page: async (after, through = null) => {
      const end = through ?? entries.length, selected = entries.slice(after, Math.min(after + 500, end));
      return { schemaVersion: 1, after: mark(after), through: mark(end), entries: selected, next: mark(after + selected.length) };
    }, acknowledge: async value => { calls.push(value); checkpoint = value; return value; } };
  const repository = { kind: 'fixture', environment: 'staging', manifests: () => structuredClone(manifests),
    write: (kind, value) => {
      const snapshotId = createHash('sha256').update(JSON.stringify(value)).digest('hex');
      objects.set(snapshotId, structuredClone(value));
      if (kind === 'manifest') manifests.push({ snapshotId, authority: value.authority });
      return snapshotId;
    }, read: (kind, id) => { if (!objects.has(id)) throw Error('missing'); return structuredClone(objects.get(id)); } };
  return { entries, mark, objects, manifests, calls, source, repository };
}

test('replication verifies complete durable content before acknowledging and retries the same identity', async () => {
  const f = fixture(501);
  const result = await replicateJournal(f.source, f.repository);
  assert.deepEqual(result.authority, f.mark(501));
  assert.equal(f.calls.length, 1);
  assert.equal(result.pageCount, 2);
  const first = f.calls[0];
  await replicateJournal(f.source, f.repository);
  assert.deepEqual(f.calls[1], first);
  assert.equal(f.manifests.length, 1, 'Unchanged authority must not create another snapshot');
  const verified = readCurrentReplica(f.repository, [f.mark(1), f.mark(500), f.mark(501)]);
  assert.deepEqual(verified.manifest.authority, f.mark(501));
});

test('partial write, read-back corruption and changed upper bound never acknowledge', async () => {
  for (const mode of ['write', 'read', 'upper']) {
    const f = fixture(501);
    if (mode === 'write') f.repository.write = () => { throw Error('private-storage-failure'); };
    if (mode === 'read') f.repository.read = () => ({ corrupted: true });
    if (mode === 'upper') {
      const page = f.source.page;
      f.source.page = async (after, through) => { const value = await page(after, through); if (after) value.through = f.mark(500); return value; };
    }
    await assert.rejects(replicateJournal(f.source, f.repository));
    assert.equal(f.calls.length, 0);
  }
});

test('missing external authority, an older prefix, forks and missing pages fail closed', async () => {
  const f = fixture();
  assert.throws(() => readCurrentReplica(f.repository), /journal/i);
  await replicateJournal(f.source, f.repository);
  assert.throws(() => readCurrentReplica(f.repository, [{ revision: 3, digest: 'a'.repeat(64) }]), /journal/i);
  const fork = { snapshotId: 'b'.repeat(64), authority: { revision: 1, digest: 'c'.repeat(64) } };
  f.manifests.push(fork);
  assert.throws(() => readCurrentReplica(f.repository), /journal/i);
  f.manifests.pop();
  const manifest = f.objects.get(f.manifests[0].snapshotId);
  f.objects.delete(manifest.pages[0].snapshotId);
  await assert.rejects(replicateJournal(f.source, f.repository));
  assert.equal(f.calls.length, 1);
});

test('lost checkpoint response is recovered without another identity or journal fork', async () => {
  const f = fixture(); const acknowledge = f.source.acknowledge;
  f.source.acknowledge = async value => { await acknowledge(value); throw Error('response lost'); };
  await assert.rejects(replicateJournal(f.source, f.repository));
  f.source.acknowledge = acknowledge;
  await replicateJournal(f.source, f.repository);
  assert.deepEqual(f.calls[0], f.calls[1]);
});

test('a fixture cannot acknowledge a real source and empty authority needs a stored genesis manifest', async () => {
  const f = fixture(0);
  f.source.kind = 'remote';
  await assert.rejects(replicateJournal(f.source, f.repository), /fixture/i);
  assert.equal(f.calls.length, 0);
  f.source.kind = 'fixture';
  const result = await replicateJournal(f.source, f.repository);
  assert.deepEqual(result.authority, GENESIS);
  assert.equal(f.manifests.length, 1);
  assert.equal(f.calls.length, 1);
});

test('extension reuses sealed pages, replaces the partial page and selects revision over time', async () => {
  const f = fixture(1001), original = f.source.page;
  f.source.page = (after, through) => original(after, through ?? 501);
  await replicateJournal(f.source, f.repository);
  const first = structuredClone(f.objects.get(f.manifests[0].snapshotId));
  f.source.page = original;
  await replicateJournal(f.source, f.repository);
  const latest = readCurrentReplica(f.repository).manifest;
  assert.deepEqual(latest.authority, f.mark(1001));
  assert.equal(latest.pages[0].snapshotId, first.pages[0].snapshotId);
  assert.notEqual(latest.pages[1].snapshotId, first.pages[1].snapshotId);
  // Wall-clock order cannot replace the newest deletion authority.
  f.objects.get(f.manifests[0].snapshotId).createdAt = '2099-01-01T00:00:00.000Z';
  assert.deepEqual(readCurrentReplica(f.repository).manifest.authority, f.mark(1001));
});

test('a cross-page duplicate cannot publish a manifest or acknowledge', async () => {
  const f = fixture(501);
  f.entries[500].targetId = f.entries[0].targetId;
  f.entries[500].digest = entryDigest(f.entries[500]);
  await assert.rejects(replicateJournal(f.source, f.repository));
  assert.equal(f.calls.length, 0);
  assert.equal(f.manifests.length, 0);
});
