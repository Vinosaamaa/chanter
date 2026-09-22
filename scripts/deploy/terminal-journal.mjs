import { createHash } from 'node:crypto';

export const GENESIS = Object.freeze({ revision: 0, digest: '0'.repeat(64) });
export const MAX_PAGE = 500;
export const MAX_ENTRIES = 250_000;
export const MAX_PAGE_BYTES = 256 * 1024;
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
const DIGEST = /^[a-f0-9]{64}$/;
const NIL = '00000000-0000-0000-0000-000000000000';
const failure = () => { throw new Error('Invalid terminal journal authority'); };

export function exactFields(value, fields) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || Object.keys(value).length !== fields.length || fields.some(key => !Object.hasOwn(value, key))) failure();
}
export function nonzeroUuid(value) {
  if (typeof value !== 'string' || !UUID.test(value) || value === NIL) failure();
  return value;
}
export function validateWatermark(value) {
  exactFields(value, ['revision', 'digest']);
  if (!Number.isSafeInteger(value.revision) || value.revision < 0 || !DIGEST.test(value.digest ?? '')
      || (value.revision === 0 && value.digest !== GENESIS.digest)) failure();
  return value;
}
export function sameWatermark(left, right) {
  validateWatermark(left); validateWatermark(right);
  return left.revision === right.revision && left.digest === right.digest;
}
export function environmentName(value) {
  if (!['staging', 'production'].includes(value)) failure();
  return value;
}

function javaInstant(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(value)) failure();
  const parsed = new Date(value);
  if (!Number.isFinite(parsed.getTime())) failure();
  const normalized = parsed.toISOString();
  if (normalized.replace('.000Z', 'Z') !== value.replace('.000Z', 'Z')) failure();
  return normalized.replace('.000Z', 'Z');
}

export function entryDigest(entry) {
  if (!Number.isSafeInteger(entry?.revision) || entry.revision < 1
      || !UUID.test(entry.eventId ?? '') || !['ACCOUNT', 'STUDY_SERVER', 'RESOURCE'].includes(entry.targetKind)
      || entry.action !== 'DELETE' || !DIGEST.test(entry.previousDigest ?? '')) failure();
  nonzeroUuid(entry.targetId);
  const canonical = ['1', String(entry.revision), entry.eventId, entry.targetKind, entry.targetId,
    'DELETE', javaInstant(entry.deletedAt), entry.previousDigest, ''].join('\n');
  return createHash('sha256').update(canonical, 'utf8').digest('hex');
}

export function validatePage(page, expectedAfter, expectedThrough = null) {
  exactFields(page, ['schemaVersion', 'after', 'through', 'entries', 'next']);
  if (page.schemaVersion !== 1 || !Array.isArray(page.entries) || page.entries.length > MAX_PAGE
      || Buffer.byteLength(JSON.stringify(page)) > MAX_PAGE_BYTES) failure();
  validateWatermark(page.after); validateWatermark(page.through); validateWatermark(page.next);
  if (!sameWatermark(page.after, expectedAfter) || (expectedThrough && !sameWatermark(page.through, expectedThrough))
      || page.after.revision > page.through.revision || page.next.revision > page.through.revision) failure();
  let cursor = page.after;
  const targets = new Set(), events = new Set();
  for (const entry of page.entries) {
    exactFields(entry, ['revision', 'eventId', 'targetKind', 'targetId', 'action', 'deletedAt', 'previousDigest', 'digest']);
    if (entry.digest !== entryDigest(entry) || entry.revision !== cursor.revision + 1
        || entry.previousDigest !== cursor.digest || targets.has(`${entry.targetKind}:${entry.targetId}`)
        || events.has(entry.eventId)) failure();
    targets.add(`${entry.targetKind}:${entry.targetId}`); events.add(entry.eventId);
    cursor = { revision: entry.revision, digest: entry.digest };
  }
  if (!sameWatermark(cursor, page.next) || (!page.entries.length && page.after.revision < page.through.revision)
      || (page.next.revision === page.through.revision && !sameWatermark(page.next, page.through))) failure();
  return page;
}

/** UUIDv8 identifies the same environment/prefix on retries; it is not an external storage attestation. */
export function checkpointIdentity(environment, watermark) {
  environmentName(environment); validateWatermark(watermark);
  const bytes = createHash('sha256').update(`chanter-terminal-checkpoint-v1\n${environment}\n${watermark.revision}\n${watermark.digest}\n`).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x80; bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
