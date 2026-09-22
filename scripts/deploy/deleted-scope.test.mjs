import test from 'node:test';
import assert from 'node:assert/strict';
import { GENESIS, entryDigest } from './terminal-journal.mjs';
import { START, ScopeVerifier, scopeStartDigest, recoveryScopeBasis, validateScopeReceipt } from './deleted-scope.mjs';

const entry = { revision: 1, eventId: '11111111-1111-4111-8111-111111111111', targetKind: 'STUDY_SERVER',
  targetId: '22222222-2222-4222-8222-222222222222', action: 'DELETE', deletedAt: '2026-09-19T06:00:00.120Z',
  retentionPolicy: 'PRESERVE_MODERATION_RECORDS_V1', previousDigest: GENESIS.digest };
entry.digest = entryDigest(entry);
const ids = ['00000000-0000-0000-0000-000000000001', '7fffffff-ffff-ffff-ffff-ffffffffffff',
  '80000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'];
const digest = '97231f4c00a42affd8a4517b3b0eeb7a4dce2db8c322eda8a29c9529add08c73';
const page = (selected = ids, after = START, nextAfter = null) => ({ schemaVersion: 1, studyServerId: entry.targetId,
  terminalRevision: entry.revision, terminalEventId: entry.eventId, terminalDigest: entry.digest, kind: 'COURSE',
  after, totalCount: 4, scopeDigest: digest, ids: [...selected], nextAfter });

test('scope chain matches the Java fixture across unsigned UUID order and bounded pages', () => {
  assert.equal(entry.digest, 'faabc05c784b94a62e6a28b0260b5531ac3eda3520a019d5af3dc9e3ccbdaaac');
  const verifier = new ScopeVerifier(entry, 'COURSE');
  verifier.accept(page(ids.slice(0, 2), START, ids[1]));
  assert.equal(verifier.complete, false);
  verifier.accept(page(ids.slice(2), ids[1]));
  assert.deepEqual(verifier.result(), { totalCount: 4, scopeDigest: digest, after: ids[3] });
  assert.throws(() => verifier.accept(page()), /scope/i);
});

test('missing, truncated, duplicated, reordered, changed or incorrectly bound scope fails closed', () => {
  for (const change of [p => { p.ids.reverse(); }, p => { p.ids[2] = p.ids[1]; }, p => { p.ids = p.ids.slice(0, 3); },
    p => { p.totalCount = 5; }, p => { p.scopeDigest = 'a'.repeat(64); }, p => { p.terminalDigest = 'b'.repeat(64); },
    p => { p.studyServerId = entry.eventId; }, p => { p.terminalRevision++; }, p => { p.after = ids[0]; },
    p => { p.nextAfter = ids[2]; }, p => { p.extra = true; }, p => { p.totalCount = 250001; }]) {
    const changed = page(); change(changed);
    assert.throws(() => new ScopeVerifier(entry, 'COURSE').accept(changed));
  }
  const partial = new ScopeVerifier(entry, 'COURSE');
  partial.accept(page(ids.slice(0, 2), START, ids[1]));
  assert.throws(() => partial.result());
  assert.throws(() => partial.accept(page(ids.slice(2), START)));
});

test('empty scope needs an explicit correctly hashed page and receipts bind full original authority', () => {
  const empty = { ...page([]), totalCount: 0, scopeDigest: scopeStartDigest(entry.digest, 'COURSE', 0) };
  const verifier = new ScopeVerifier(entry, 'COURSE'); verifier.accept(empty);
  assert.deepEqual(verifier.result(), { totalCount: 0, scopeDigest: empty.scopeDigest, after: START });
  const receipt = { ...empty, receivedCount: 0, ready: true };
  delete receipt.ids; delete receipt.nextAfter;
  assert.equal(validateScopeReceipt(receipt, entry, 'COURSE', verifier.result(), true), receipt);
  for (const changed of [{ ...receipt, ready: false }, { ...receipt, scopeDigest: digest },
    { ...receipt, receivedCount: 1 }, { ...receipt, after: ids[0] }, { ...receipt, terminalEventId: entry.targetId }])
    assert.throws(() => validateScopeReceipt(changed, entry, 'COURSE', verifier.result(), true));
});

test('derived chain matches the owning Java historical-union fixture without replacing current authority', () => {
  const recovery = { restoreId: '33333333-3333-4333-8333-333333333333', originalScopeDigest: digest };
  assert.equal(recoveryScopeBasis(recovery.restoreId, entry, 'COURSE', digest),
    '03327b158e2536a1325cfdef9f19a0feaf58f49e41d8fa549c266db60cbbbc49');
  const derived = { ...page([ids[0], '00000000-0000-0000-0000-000000000002', ...ids.slice(1)]), totalCount: 5,
    scopeDigest: 'a3dfbf38520153bfb607a2b163b9de71f4ea5287364b035ade2b8079bd1e9c03' };
  const verifier = new ScopeVerifier(entry, 'COURSE', recovery); verifier.accept(derived);
  assert.equal(verifier.result().totalCount, 5);
  assert.throws(() => new ScopeVerifier(entry, 'COURSE').accept(derived));
  assert.throws(() => new ScopeVerifier(entry, 'COURSE', { ...recovery, restoreId: entry.targetId }).accept(derived));
  assert.throws(() => new ScopeVerifier(entry, 'COURSE', { ...recovery, originalScopeDigest: 'a'.repeat(64) }).accept(derived));
});
