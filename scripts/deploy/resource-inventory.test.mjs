import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { InventoryVerifier, inventoryRestorable } from './resource-inventory.mjs';

const hash = value => createHash('sha256').update(value).digest('hex');
function fixtureInventory() {
  const courseId = '11111111-1111-4111-8111-111111111111';
  const snapshot = { schemaVersion: 1, inventoryId: '44444444-4444-4444-8444-444444444444',
    databaseBackupId: '55555555-5555-4555-8555-555555555555', authority: { revision: 0, digest: '0'.repeat(64) },
    namespaceSha256: 'a'.repeat(64), capturedAt: '2026-09-22T00:00:00Z', referenceCount: 3, referenceDigest: '' };
  const references = [1, 2, 3].map((ordinal) => {
    const resourceId = `${ordinal}${'2'.repeat(7)}-2222-4222-8222-222222222222`;
    return { ordinal, resourceId, courseId, referenceKind: 'CURRENT', storageBackend: 'local',
      key: `resources/v1/${courseId}/${resourceId}/33333333-3333-4333-8333-333333333333`,
      byteSize: 3, sha256: hash(Buffer.from([1, 2, 3])), resourceState: ordinal === 2 ? 'QUARANTINED' : 'AVAILABLE',
      sourceRetained: true, terminal: ordinal === 3, providerVersionId: null };
  });
  let digest = hash(`resource-recovery-inventory\n1\n${snapshot.inventoryId}\n${snapshot.databaseBackupId}\n0\n${snapshot.authority.digest}\n${snapshot.namespaceSha256}\n`);
  for (const row of references) digest = hash(`${digest}\n${row.resourceId}\n${row.referenceKind}\n${row.storageBackend}\n${row.key}\n${row.byteSize}\n${row.sha256}\n${row.resourceState}\n${row.sourceRetained}\n${row.terminal}\n`);
  snapshot.referenceDigest = digest;
  const readPage = (after, limit) => ({ schemaVersion: 1, snapshot: structuredClone(snapshot), after,
    references: structuredClone(references.slice(after, after + limit)),
    nextAfter: Math.min(after + limit, references.length) < references.length ? after + limit : null });
  return { snapshot, references, readPage };
}

test('owning inventory chain preserves private and quarantine rows and explicit terminal exclusion', () => {
  const { snapshot, references, readPage } = fixtureInventory();
  const verifier = new InventoryVerifier(snapshot);
  assert.equal(verifier.accept(readPage(0, 2)), false);
  assert.equal(verifier.accept(readPage(2, 2)), true);
  assert.deepEqual(references.map(inventoryRestorable), [true, true, false]);
  assert.throws(() => verifier.accept(readPage(3, 1)));
});

test('partial pages, changed snapshot, duplicates, order and unsupported physical tuples fail closed', () => {
  const { snapshot, readPage } = fixtureInventory();
  for (const mutate of [
    page => { page.references.pop(); },
    page => { page.after = 1; },
    page => { page.nextAfter = 3; },
    page => { page.snapshot.databaseBackupId = snapshot.inventoryId; },
    page => { page.references[1] = page.references[0]; },
    page => { page.references.reverse(); },
    page => { page.references[0].providerVersionId = 'unknown-version'; },
    page => { page.references[0].terminal = false; page.references[2].terminal = false; },
    page => { page.references[0].referenceKind = 'MIGRATION'; },
    page => { page.references[0].key = '../outside'; },
  ]) {
    const page = readPage(0, 3); mutate(page);
    assert.throws(() => new InventoryVerifier(snapshot).accept(page));
  }
});

test('empty inventory requires its explicit final page and exact empty chain', () => {
  const { snapshot } = fixtureInventory(); snapshot.referenceCount = 0;
  snapshot.referenceDigest = hash(`resource-recovery-inventory\n1\n${snapshot.inventoryId}\n${snapshot.databaseBackupId}\n0\n${snapshot.authority.digest}\n${snapshot.namespaceSha256}\n`);
  const verifier = new InventoryVerifier(snapshot);
  assert.equal(verifier.complete, false);
  assert.equal(verifier.accept({ schemaVersion: 1, snapshot, after: 0, references: [], nextAfter: null }), true);
  assert.throws(() => new InventoryVerifier({ ...snapshot, referenceCount: 250_001 }));
});
