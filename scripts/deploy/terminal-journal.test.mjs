import test from 'node:test';
import assert from 'node:assert/strict';
import { GENESIS, entryDigest, validatePage, checkpointIdentity } from './terminal-journal.mjs';

const entry = () => ({ revision: 1, eventId: '11111111-1111-4111-8111-111111111111', targetKind: 'ACCOUNT',
  targetId: '22222222-2222-4222-8222-222222222222', action: 'DELETE', retentionPolicy: 'PRESERVE_MODERATION_RECORDS_V1', deletedAt: '2026-09-19T06:00:00.120Z',
  previousDigest: '0'.repeat(64), digest: 'b0d23bf4b8f953242aed4725ef4cf5cc1f01d6d2e957454c67e8ddec9a6a4c72' });
const mark = value => ({ revision: value.revision, digest: value.digest });
const page = () => ({ schemaVersion: 2, after: GENESIS, through: mark(entry()), entries: [entry()], next: mark(entry()) });

test('canonical digest matches the actual Java251 contract including trailing newline and milliseconds', () => {
  assert.equal(entryDigest(entry()), entry().digest);
  assert.deepEqual(validatePage(page(), GENESIS), page());
  const first = { ...entry(), deletedAt: '2026-09-19T06:00:00Z' };
  assert.equal(entryDigest(first), entryDigest({ ...first, deletedAt: '2026-09-19T06:00:00.000Z' }));
});

test('pages reject corrupted authority, discontinuities, private extra fields and changed bounds', () => {
  for (const change of [
    p => { p.entries[0].targetId = '33333333-3333-4333-8333-333333333333'; },
    p => { p.entries[0].previousDigest = 'a'.repeat(64); },
    p => { p.entries[0].revision = 2; p.entries[0].digest = entryDigest(p.entries[0]); },
    p => { p.entries[0].action = 'RESTORE'; },
    p => { p.entries[0].targetKind = 'EMAIL'; },
    p => { p.entries[0].targetId = '00000000-0000-0000-0000-000000000000'; },
    p => { p.entries[0].deletedAt = '2026-09-19T06:00:00.120001Z'; },
    p => { p.entries[0].deletedAt = '2026-02-30T06:00:00Z'; },
    p => { p.entries[0].revision = Number.MAX_SAFE_INTEGER + 1; },
    p => { p.entries[0].privateContent = 'must-not-survive'; },
    p => { delete p.entries[0].retentionPolicy; },
    p => { p.entries[0].retentionPolicy = 'PURGE_ALL'; },
    p => { p.next.digest = 'a'.repeat(64); },
    p => { p.through.digest = 'b'.repeat(64); },
    p => { p.entries = []; },
    p => { p.schemaVersion = 1; },
  ]) {
    const value = structuredClone(page()); change(value);
    assert.throws(() => validatePage(value, GENESIS, page().through), /journal/i);
  }
  assert.throws(() => validatePage(page(), { revision: 0, digest: 'a'.repeat(64) }), /journal/i);
});

test('empty genesis is explicit and duplicate targets or oversized pages fail closed', () => {
  const empty = { schemaVersion: 2, after: GENESIS, through: GENESIS, entries: [], next: GENESIS };
  assert.deepEqual(validatePage(empty, GENESIS), empty);
  assert.throws(() => validatePage(null, GENESIS), /journal/i);
  const repeated = { ...entry(), revision: 2, eventId: '33333333-3333-4333-8333-333333333333', previousDigest: entry().digest };
  repeated.digest = entryDigest(repeated);
  assert.throws(() => validatePage({ ...page(), entries: [entry(), repeated], next: mark(repeated), through: mark(repeated) }, GENESIS), /journal/i);
  assert.throws(() => validatePage({ ...page(), entries: Array(501).fill(entry()) }, GENESIS), /journal/i);
});

test('checkpoint identity is stable across retries and separates environments and prefixes', () => {
  const first = checkpointIdentity('staging', page().through);
  assert.match(first, /^[a-f0-9]{8}-[a-f0-9]{4}-8[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
  assert.equal(first, checkpointIdentity('staging', structuredClone(page().through)));
  assert.notEqual(first, checkpointIdentity('production', page().through));
  assert.notEqual(first, checkpointIdentity('staging', GENESIS));
  assert.throws(() => checkpointIdentity('unknown', GENESIS), /journal/i);
});
