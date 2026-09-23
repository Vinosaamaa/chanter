import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { exactFields, nonzeroUuid, validateWatermark } from './terminal-journal.mjs';

export const MAX_INVENTORY_REFERENCES = 250_000;
export const MAX_INVENTORY_PAGE_BYTES = 512 * 1024;
const HEX = /^[a-f0-9]{64}$/;
const STATES = ['AVAILABLE', 'QUARANTINED', 'SCAN_FAILED', 'REJECTED', 'DELETE_PENDING', 'DELETED'];
const hash = value => createHash('sha256').update(value, 'utf8').digest('hex');
const fail = () => { throw new Error('Invalid source resource inventory'); };

export function inventorySnapshot(value) {
  exactFields(value, ['schemaVersion', 'inventoryId', 'databaseBackupId', 'authority', 'namespaceSha256',
    'capturedAt', 'referenceCount', 'referenceDigest']);
  nonzeroUuid(value.inventoryId); nonzeroUuid(value.databaseBackupId); validateWatermark(value.authority);
  if (value.schemaVersion !== 1 || !HEX.test(value.namespaceSha256 ?? '') || !HEX.test(value.referenceDigest ?? '')
      || !Number.isSafeInteger(value.referenceCount) || value.referenceCount < 0 || value.referenceCount > MAX_INVENTORY_REFERENCES
      || typeof value.capturedAt !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,3})?Z$/.test(value.capturedAt)
      || !Number.isFinite(Date.parse(value.capturedAt))) fail();
  return structuredClone(value);
}

export function inventoryObject(reference) {
  return { resourceId: reference.resourceId, courseId: reference.courseId, key: reference.key,
    byteSize: reference.byteSize, sha256: reference.sha256, providerVersionId: null,
    disposition: 'EXTANT', storageWriteSettled: true };
}
export const inventoryRestorable = reference => !reference.terminal && reference.sourceRetained
  && ['AVAILABLE', 'QUARANTINED', 'SCAN_FAILED'].includes(reference.resourceState);

/** Mirrors the owning media snapshot digest and consumes one bounded page at a time. */
export class InventoryVerifier {
  #snapshot; #digest; #after = 0; #last = null; #complete = false;
  constructor(snapshot) {
    this.#snapshot = inventorySnapshot(snapshot);
    this.#digest = hash(`resource-recovery-inventory\n1\n${snapshot.inventoryId}\n${snapshot.databaseBackupId}\n`
      + `${snapshot.authority.revision}\n${snapshot.authority.digest}\n${snapshot.namespaceSha256}\n`);
  }
  get after() { return this.#after; }
  get complete() { return this.#complete; }
  accept(page) {
    exactFields(page, ['schemaVersion', 'snapshot', 'after', 'references', 'nextAfter']);
    if (this.#complete || Buffer.byteLength(JSON.stringify(page)) > MAX_INVENTORY_PAGE_BYTES
        || page.schemaVersion !== 1 || !isDeepStrictEqual(inventorySnapshot(page.snapshot), this.#snapshot)
        || page.after !== this.#after || !Array.isArray(page.references) || page.references.length > 256) fail();
    for (const reference of page.references) {
      exactFields(reference, ['ordinal', 'resourceId', 'courseId', 'referenceKind', 'storageBackend', 'key', 'byteSize',
        'sha256', 'resourceState', 'sourceRetained', 'terminal', 'providerVersionId']);
      nonzeroUuid(reference.resourceId); nonzeroUuid(reference.courseId);
      if (reference.ordinal !== this.#after + 1 || reference.ordinal > this.#snapshot.referenceCount
          || !['CURRENT', 'MIGRATION'].includes(reference.referenceKind) || !['local', 's3'].includes(reference.storageBackend)
          || typeof reference.key !== 'string' || reference.key.length > 150 || !HEX.test(reference.sha256 ?? '')
          || !Number.isSafeInteger(reference.byteSize) || reference.byteSize < 1 || reference.byteSize > 10 * 1024 * 1024
          || !STATES.includes(reference.resourceState) || typeof reference.sourceRetained !== 'boolean'
          || typeof reference.terminal !== 'boolean' || reference.providerVersionId !== null) fail();
      const parts = reference.key.split('/');
      if (parts.length !== 5 || parts[0] !== 'resources' || parts[1] !== 'v1'
          || parts[2] !== reference.courseId || parts[3] !== reference.resourceId) fail();
      nonzeroUuid(parts[4]);
      if (this.#last?.resourceId === reference.resourceId) {
        if (this.#last.referenceKind !== 'CURRENT' || reference.referenceKind !== 'MIGRATION'
            || this.#last.key === reference.key) fail();
        for (const field of ['courseId', 'storageBackend', 'byteSize', 'sha256', 'resourceState', 'sourceRetained', 'terminal'])
          if (this.#last[field] !== reference[field]) fail();
      } else if (reference.referenceKind !== 'CURRENT' || (this.#last && this.#last.resourceId >= reference.resourceId)) fail();
      this.#digest = hash(`${this.#digest}\n${reference.resourceId}\n${reference.referenceKind}\n${reference.storageBackend}\n`
        + `${reference.key}\n${reference.byteSize}\n${reference.sha256}\n${reference.resourceState}\n${reference.sourceRetained}\n${reference.terminal}\n`);
      this.#after = reference.ordinal; this.#last = structuredClone(reference);
    }
    if (this.#after === this.#snapshot.referenceCount) {
      if (page.nextAfter !== null || this.#digest !== this.#snapshot.referenceDigest) fail();
      this.#complete = true;
    } else if (page.references.length === 0 || page.nextAfter !== this.#after) fail();
    return this.#complete;
  }
}
