import { createHash } from 'node:crypto';
import { GENESIS, MAX_ENTRIES, MAX_PAGE, checkpointIdentity, environmentName, exactFields,
  nonzeroUuid, sameWatermark, validatePage, validateWatermark } from './terminal-journal.mjs';
import { MAX_MANIFEST_BYTES, MAX_MANIFESTS } from './terminal-journal-storage.mjs';

const HEX = /^[a-f0-9]{64}$/;
const MAX_PAGES = Math.ceil(MAX_ENTRIES / MAX_PAGE);
const fail = () => { throw new Error('Invalid terminal journal replica'); };
const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const mark = entry => ({ revision: entry.revision, digest: entry.digest });
const deadline = () => performance.now() + 15 * 60_000;
const within = end => { if (performance.now() > end) fail(); };
function requireMark(expected, value) {
  validateWatermark(value);
  if (value.revision > MAX_ENTRIES || (expected.has(value.revision) && expected.get(value.revision) !== value.digest)) fail();
  expected.set(value.revision, value.digest);
}

function checkpoint(value, environment) {
  exactFields(value, ['revision', 'digest', 'checkpointId']);
  validateWatermark(mark(value)); nonzeroUuid(value.checkpointId);
  if (value.checkpointId !== checkpointIdentity(environment, mark(value))) fail();
  return mark(value);
}

function manifest(value, environment) {
  exactFields(value, ['schemaVersion', 'environment', 'checkpointId', 'authority', 'createdAt', 'pages']);
  validateWatermark(value.authority);
  if (value.schemaVersion !== 1 || value.environment !== environment
      || value.authority.revision > MAX_ENTRIES || !Array.isArray(value.pages) || value.pages.length > MAX_PAGES
      || Buffer.byteLength(JSON.stringify(value)) > MAX_MANIFEST_BYTES
      || typeof value.createdAt !== 'string' || !Number.isFinite(Date.parse(value.createdAt))
      || new Date(value.createdAt).toISOString() !== value.createdAt
      || value.checkpointId !== checkpointIdentity(environment, value.authority)) fail();
  for (const ref of value.pages) {
    exactFields(ref, ['snapshotId', 'sha256', 'after', 'next', 'count']);
    validateWatermark(ref.after); validateWatermark(ref.next);
    if (!HEX.test(ref.snapshotId) || !HEX.test(ref.sha256) || !Number.isInteger(ref.count)
        || ref.count < 1 || ref.count > MAX_PAGE || ref.next.revision - ref.after.revision !== ref.count) fail();
  }
  return value;
}

/** Select by revision, never snapshot time. A corrupt newest prefix must not fall back to an older one. */
export function readCurrentReplica(repository, required = [], options = {}) {
  const end = options.deadline ?? deadline();
  environmentName(repository.environment);
  const listing = repository.manifests();
  if (!Array.isArray(listing) || listing.length > MAX_MANIFESTS) fail();
  if (!listing.length) {
    if (options.allowMissing && !required.length) return null;
    fail();
  }
  const expected = new Map();
  for (const value of required) requireMark(expected, value);
  let selected;
  for (const item of listing) {
    exactFields(item, ['snapshotId', 'authority']);
    if (!HEX.test(item.snapshotId)) fail();
    requireMark(expected, item.authority);
    if (!selected || item.authority.revision > selected.authority.revision
        || (item.authority.revision === selected.authority.revision && item.snapshotId < selected.snapshotId)) selected = item;
  }
  const value = manifest(repository.read('manifest', selected.snapshotId), repository.environment);
  if (!sameWatermark(value.authority, selected.authority)) fail();
  verifyChain(repository, value, expected, end);
  return { manifest: value, snapshotId: selected.snapshotId };
}

function verifyChain(repository, value, expected, end) {
  const encountered = new Map([[0, GENESIS.digest]]), targets = new Set(), events = new Set();
  let cursor = GENESIS;
  for (const ref of value.pages) {
    within(end);
    if (!sameWatermark(ref.after, cursor)) fail();
    const page = validatePage(repository.read('page', ref.snapshotId), cursor);
    if (hash(page) !== ref.sha256 || page.entries.length !== ref.count || !sameWatermark(page.next, ref.next)
        || page.through.revision > value.authority.revision) fail();
    requireMark(expected, page.through);
    for (const entry of page.entries) {
      const target = `${entry.targetKind}:${entry.targetId}`;
      if (targets.has(target) || events.has(entry.eventId)) fail();
      targets.add(target); events.add(entry.eventId); encountered.set(entry.revision, entry.digest);
    }
    cursor = page.next;
  }
  if (!sameWatermark(cursor, value.authority)) fail();
  for (const [revision, digest] of expected) if (encountered.get(revision) !== digest) fail();
  within(end);
}

/** No scheduler or retry loop: the existing backup runner owns retries after any failure. */
export async function replicateJournal(source, repository) {
  if (!['remote', 'fixture'].includes(source.kind) || source.kind !== repository.kind)
    throw new Error('Terminal journal source and fixture storage must not be mixed');
  const end = deadline(), acknowledged = await source.checkpoint();
  const required = acknowledged === null ? [] : [checkpoint(acknowledged, repository.environment)];
  const existing = readCurrentReplica(repository, required, { allowMissing: acknowledged === null, deadline: end });
  if (existing) required.push(existing.manifest.authority);
  const refs = existing ? existing.manifest.pages.filter(ref => ref.count === MAX_PAGE) : [];
  // Only the final partial page may be replaced. Earlier immutable pages remain reusable.
  if (existing && refs.some((ref, index) => ref !== existing.manifest.pages[index])) fail();
  let cursor = refs.at(-1)?.next ?? GENESIS;
  let fetched = cursor, through = null, buffer = [], requests = 0;
  const writePage = entries => {
    const next = mark(entries.at(-1));
    const page = { schemaVersion: 1, after: cursor, through, entries, next };
    validatePage(page, cursor, through);
    refs.push({ snapshotId: repository.write('page', page), sha256: hash(page), after: cursor, next, count: entries.length });
    cursor = next;
  };
  do {
    within(end);
    if (++requests > MAX_PAGES * 2 + 1) fail();
    const page = validatePage(await source.page(fetched.revision, through?.revision ?? null), fetched, through);
    through ??= page.through;
    if (through.revision > MAX_ENTRIES || (existing && through.revision < existing.manifest.authority.revision)) fail();
    if (existing && sameWatermark(through, existing.manifest.authority)) break;
    buffer.push(...page.entries);
    while (buffer.length >= MAX_PAGE) writePage(buffer.splice(0, MAX_PAGE));
    fetched = page.next;
  } while (!sameWatermark(fetched, through));
  if (!existing || !sameWatermark(through, existing.manifest.authority)) {
    if (buffer.length) writePage(buffer);
    if (!sameWatermark(cursor, through)) fail();
    const candidate = manifest({ schemaVersion: 1, environment: repository.environment,
      checkpointId: checkpointIdentity(repository.environment, through), authority: through,
      createdAt: new Date().toISOString(), pages: refs }, repository.environment);
    const expected = new Map();
    for (const value of required) requireMark(expected, value);
    // Malformed source data must not publish a corrupt newest manifest and poison later recovery.
    verifyChain(repository, candidate, expected, end);
    repository.write('manifest', candidate);
  }
  // Read the complete independently stored prefix, including reused pages, before the only acknowledgement call.
  const verified = readCurrentReplica(repository, [...required, through], { deadline: end });
  if (!sameWatermark(verified.manifest.authority, through)) fail();
  within(end);
  const receipt = { ...through, checkpointId: verified.manifest.checkpointId };
  const response = await source.acknowledge(receipt);
  checkpoint(response, repository.environment);
  if (!sameWatermark(mark(response), through) || response.checkpointId !== receipt.checkpointId) fail();
  return { authority: through, checkpointId: receipt.checkpointId, pageCount: verified.manifest.pages.length };
}
