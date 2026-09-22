import { GENESIS, exactFields, nonzeroUuid, sameWatermark, validatePage, validateWatermark } from './terminal-journal.mjs';
import { readCurrentReplica } from './terminal-journal-replica.mjs';

export const SOURCES = Object.freeze(['auth', 'community', 'message', 'media', 'agent', 'notification', 'search']);
const SCOPES = Object.freeze({ auth: 'ALL_BROWSER_SESSIONS', agent: 'ALL_PENDING_NATIVE_REQUESTS' });
const fail = () => { throw new Error('Current terminal authority recovery remains isolated'); };

function participantReceipt(receipt, source, authority = null) {
  exactFields(receipt, ['schemaVersion', 'source', 'authority', 'pendingTargets', 'preservedTargets']);
  validateWatermark(receipt.authority);
  if (receipt.schemaVersion !== 1 || receipt.source !== source
      || (authority && !sameWatermark(receipt.authority, authority))
      || !Number.isSafeInteger(receipt.pendingTargets) || receipt.pendingTargets < 0
      || !Number.isSafeInteger(receipt.preservedTargets) || receipt.preservedTargets < 0
      || receipt.pendingTargets + receipt.preservedTargets > receipt.authority.revision) fail();
  return receipt;
}

function invalidationReceipt(receipt, source, recoveryId, authority) {
  exactFields(receipt, ['schemaVersion', 'source', 'recoveryId', 'authority', 'invalidatedAt', 'scope']);
  if (receipt.schemaVersion !== 1 || receipt.source !== source || receipt.recoveryId !== recoveryId
      || !sameWatermark(receipt.authority, authority) || receipt.scope !== SCOPES[source]
      || typeof receipt.invalidatedAt !== 'string'
      || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(receipt.invalidatedAt)
      || !Number.isFinite(Date.parse(receipt.invalidatedAt))
      || new Date(receipt.invalidatedAt).toISOString().replace('.000Z', 'Z') !== receipt.invalidatedAt.replace('.000Z', 'Z')) fail();
  return receipt;
}

/** This proves only the authority stage. The owning operator command must independently establish isolation. */
export async function recoverCurrentAuthority({ repository, clients, recoveryId, requiredAuthority, restoredCheckpoint = null }) {
  nonzeroUuid(recoveryId); validateWatermark(requiredAuthority); exactFields(clients, SOURCES);
  const end = performance.now() + 15 * 60_000;
  const within = () => { if (performance.now() > end) fail(); };
  const required = [requiredAuthority];
  if (restoredCheckpoint) required.push(validateWatermark(restoredCheckpoint));
  for (const source of SOURCES) {
    within();
    if (!['fixture', 'remote'].includes(repository.kind) || clients[source]?.kind !== repository.kind) fail();
    required.push(participantReceipt(await clients[source].receipt(), source).authority);
  }
  const selected = readCurrentReplica(repository, required, { deadline: end });
  const authority = selected.manifest.authority;
  // Each page is loaded and validated immediately before replay. Targets never enter the operator receipt.
  let cursor = GENESIS;
  const refs = selected.manifest.pages.length ? selected.manifest.pages : [null];
  for (const ref of refs) {
    within();
    const stored = ref ? repository.read('page', ref.snapshotId)
      : { schemaVersion: 1, after: GENESIS, through: GENESIS, entries: [], next: GENESIS };
    const page = validatePage({ ...stored, through: authority }, cursor, authority);
    for (const source of SOURCES) { within(); await clients[source].reapply(page); }
    cursor = page.next;
  }
  if (!sameWatermark(cursor, authority)) fail();
  const receipts = async () => {
    const result = [];
    for (const source of SOURCES) { within(); result.push(participantReceipt(await clients[source].receipt(), source, authority)); }
    return result;
  };
  await receipts();
  const invalidations = [];
  for (const source of Object.keys(SCOPES)) {
    within();
    invalidations.push(invalidationReceipt(await clients[source].invalidate({ recoveryId, authority }), source, recoveryId, authority));
  }
  const current = readCurrentReplica(repository, [authority], { deadline: end });
  if (!sameWatermark(current.manifest.authority, authority)) fail();
  const participants = await receipts();
  within();
  return { schemaVersion: 1, recoveryId, environment: repository.environment, status: 'current-authority-applied-isolated',
    authority, checkpointId: selected.manifest.checkpointId, participants, invalidations, publicCutoverAllowed: false };
}
