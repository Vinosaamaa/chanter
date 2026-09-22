import { createHash } from 'node:crypto';
import { entryDigest, exactFields, nonzeroUuid } from './terminal-journal.mjs';

export const START = '00000000-0000-0000-0000-000000000000';
export const SCOPE_KINDS = Object.freeze(['COURSE', 'CHANNEL']);
export const MAX_SCOPE_BYTES = 32 * 1024;
export const MAX_SCOPE_IDS = 250_000;
export const MAX_SCOPE_PAGES = 2048;
const HEX = /^[a-f0-9]{64}$/;
const fail = () => { throw new Error('Invalid archived deletion scope'); };
const hash = text => createHash('sha256').update(text, 'utf8').digest('hex');
const cursor = value => { if (value !== START) nonzeroUuid(value); return value; };
const kind = value => { if (!SCOPE_KINDS.includes(value)) fail(); };
const count = value => { if (!Number.isSafeInteger(value) || value < 0 || value > MAX_SCOPE_IDS) fail(); };

function bound(value, entry, expectedKind) {
  kind(expectedKind);
  if (entry.targetKind !== 'STUDY_SERVER' || entry.digest !== entryDigest(entry)
      || value.schemaVersion !== 1 || value.studyServerId !== entry.targetId
      || value.terminalRevision !== entry.revision || value.terminalEventId !== entry.eventId
      || value.terminalDigest !== entry.digest || value.kind !== expectedKind || !HEX.test(value.scopeDigest ?? '')) fail();
  nonzeroUuid(entry.eventId); count(value.totalCount); cursor(value.after);
}

export function scopeStartDigest(terminalDigest, expectedKind, totalCount) {
  kind(expectedKind); count(totalCount);
  if (!HEX.test(terminalDigest ?? '')) fail();
  return hash(`deleted-study-server-scope\n1\n${terminalDigest}\n${expectedKind}\n${totalCount}\n`);
}

export function recoveryScopeBasis(restoreId, entry, expectedKind, originalScopeDigest) {
  nonzeroUuid(restoreId); kind(expectedKind);
  if (entry.targetKind !== 'STUDY_SERVER' || entry.digest !== entryDigest(entry) || !HEX.test(originalScopeDigest ?? '')) fail();
  return hash(`deleted-study-server-recovery-scope\n1\n${restoreId}\n${entry.digest}\n${expectedKind}\n${originalScopeDigest}\n`);
}

export function recoveryScopeEnvelope(value, nestedField, restoreId, recoveryId, originalScopeDigest) {
  exactFields(value, ['schemaVersion', 'restoreId', 'recoveryId', 'originalScopeDigest', nestedField]);
  nonzeroUuid(restoreId); nonzeroUuid(recoveryId);
  if (value.schemaVersion !== 1 || value.restoreId !== restoreId || value.recoveryId !== recoveryId
      || value.originalScopeDigest !== originalScopeDigest || !HEX.test(originalScopeDigest ?? '')) fail();
  return value[nestedField];
}

/** One bounded page at a time; final count and digest qualify even an explicitly empty scope. */
export class ScopeVerifier {
  #entry; #kind; #basis; #after = START; #received = 0; #total; #expected; #digest; #complete = false;
  constructor(entry, expectedKind, recovery = null) {
    kind(expectedKind); this.#entry = entry; this.#kind = expectedKind;
    if (recovery) exactFields(recovery, ['restoreId', 'originalScopeDigest']);
    this.#basis = recovery ? recoveryScopeBasis(recovery.restoreId, entry, expectedKind, recovery.originalScopeDigest) : entry.digest;
  }
  get complete() { return this.#complete; }
  get after() { return this.#after; }
  accept(page) {
    exactFields(page, ['schemaVersion', 'studyServerId', 'terminalRevision', 'terminalEventId', 'terminalDigest',
      'kind', 'after', 'totalCount', 'scopeDigest', 'ids', 'nextAfter']);
    bound(page, this.#entry, this.#kind);
    if (this.#complete || page.after !== this.#after || !Array.isArray(page.ids) || page.ids.length > 256
        || Buffer.byteLength(JSON.stringify({ entry: this.#entry, page })) > MAX_SCOPE_BYTES) fail();
    if (this.#total === undefined) {
      this.#total = page.totalCount; this.#expected = page.scopeDigest;
      this.#digest = scopeStartDigest(this.#basis, this.#kind, this.#total);
    }
    if (page.totalCount !== this.#total || page.scopeDigest !== this.#expected
        || this.#received + page.ids.length > this.#total
        || (!page.ids.length && (this.#total !== 0 || this.#after !== START || page.nextAfter !== null))) fail();
    for (const id of page.ids) {
      nonzeroUuid(id);
      if (id <= this.#after) fail();
      this.#digest = hash(`${this.#digest}\n${id}\n`); this.#after = id; this.#received++;
    }
    if (page.nextAfter === null) {
      if (this.#received !== this.#total || this.#digest !== this.#expected) fail();
      this.#complete = true;
    } else if (!page.ids.length || page.nextAfter !== this.#after || this.#received >= this.#total) fail();
    return page;
  }
  result() {
    if (!this.#complete) fail();
    return { totalCount: this.#total, scopeDigest: this.#expected, after: this.#after };
  }
}

export function validateScopeReceipt(receipt, entry, expectedKind, expected, requireReady = false) {
  exactFields(receipt, ['schemaVersion', 'studyServerId', 'terminalRevision', 'terminalEventId', 'terminalDigest',
    'kind', 'totalCount', 'scopeDigest', 'receivedCount', 'after', 'ready']);
  bound(receipt, entry, expectedKind); count(receipt.receivedCount);
  if (receipt.totalCount !== expected.totalCount || receipt.scopeDigest !== expected.scopeDigest
      || receipt.receivedCount > receipt.totalCount || typeof receipt.ready !== 'boolean'
      || (receipt.receivedCount === 0 ? receipt.after !== START : receipt.after === START)
      || (requireReady && !receipt.ready)
      || (receipt.ready && (receipt.receivedCount !== expected.totalCount || receipt.after !== expected.after))) fail();
  return receipt;
}
