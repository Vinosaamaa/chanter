import { GENESIS, JOURNAL_SCHEMA, exactFields, nonzeroUuid, sameWatermark, validatePage, validateWatermark } from './terminal-journal.mjs';
import { readCurrentReplica } from './terminal-journal-replica.mjs';
import { ScopeVerifier, validateScopeReceipt, recoveryScopeEnvelope, MAX_SCOPE_IDS, MAX_SCOPE_PAGES } from './deleted-scope.mjs';

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
export async function recoverCurrentAuthority({ repository, clients, recoveryId, restoreId, requiredAuthority, restoredCheckpoint = null }) {
  nonzeroUuid(recoveryId); nonzeroUuid(restoreId); validateWatermark(requiredAuthority); exactFields(clients, SOURCES);
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
  const servers = new Map();
  const refs = selected.manifest.pages.length ? selected.manifest.pages : [null];
  const replay = async sources => {
    let cursor = GENESIS;
    for (const ref of refs) {
      within();
      const stored = ref ? repository.read('page', ref.snapshotId)
        : { schemaVersion: JOURNAL_SCHEMA, after: GENESIS, through: GENESIS, entries: [], next: GENESIS };
      const page = validatePage({ ...stored, through: authority }, cursor, authority);
      for (const entry of page.entries) if (entry.targetKind === 'STUDY_SERVER') servers.set(entry.revision, entry);
      for (const source of sources) { within(); await clients[source].reapply(page); }
      cursor = page.next;
    }
    if (!sameWatermark(cursor, authority)) fail();
  };
  const importScopes = async sources => {
    for (const group of selected.manifest.scopes) {
      const entry = servers.get(group.revision);
      if (!entry) fail();
      const verifier = new ScopeVerifier(entry, group.kind);
      for (const ref of group.pages) {
        within();
        const page = verifier.accept(repository.read('scope', ref.snapshotId));
        if (page.totalCount !== group.totalCount || page.scopeDigest !== group.scopeDigest) fail();
        for (const source of sources) {
          within();
          const receipt = await clients[source].importScope({ entry, page });
          validateScopeReceipt(receipt, entry, group.kind, group, verifier.complete);
        }
      }
      if (verifier.result().after !== group.after) fail();
    }
  };
  // Community may have no graph at this physical backup point. Import independently retained current scope first.
  await replay(['auth']);
  await importScopes(['community']);
  await replay(SOURCES.filter(source => source !== 'auth'));
  // Dependents require their original terminal fence before any current-scope import.
  await importScopes(SOURCES.filter(source => !['auth', 'community'].includes(source)));
  // The current archive is immutable. Extra historical relationships come only from the verified restored owner.
  let derivedCount = 0, derivedPages = 0;
  function* originalIds(entry, group) {
    const verifier = new ScopeVerifier(entry, group.kind);
    for (const ref of group.pages) {
      within();
      const page = verifier.accept(repository.read('scope', ref.snapshotId));
      if (page.totalCount !== group.totalCount || page.scopeDigest !== group.scopeDigest) fail();
      yield* page.ids;
    }
    if (verifier.result().after !== group.after) fail();
  }
  for (const entry of servers.values()) {
    within();
    const summaries = await clients.community.deriveScope({ recoveryId, entry });
    if (!Array.isArray(summaries) || summaries.length !== 2) fail();
    for (const group of selected.manifest.scopes.filter(value => value.revision === entry.revision)) {
      const matched = summaries.filter(value => value?.scope?.kind === group.kind);
      if (matched.length !== 1) fail();
      const expected = recoveryScopeEnvelope(matched[0], 'scope', restoreId, recoveryId, group.scopeDigest);
      validateScopeReceipt(expected, entry, group.kind, expected, true);
      derivedCount += expected.totalCount;
      if (expected.totalCount < group.totalCount || derivedCount > MAX_SCOPE_IDS) fail();
      const verifier = new ScopeVerifier(entry, group.kind, { restoreId, originalScopeDigest: group.scopeDigest });
      const original = originalIds(entry, group);
      let nextOriginal = original.next();
      do {
        within();
        if (++derivedPages > MAX_SCOPE_PAGES) fail();
        const response = await clients.community.recoveryScope({ recoveryId, entry, kind: group.kind, after: verifier.after, limit: 256 });
        const page = verifier.accept(recoveryScopeEnvelope(response, 'page', restoreId, recoveryId, group.scopeDigest));
        if (page.totalCount !== expected.totalCount || page.scopeDigest !== expected.scopeDigest) fail();
        for (const id of page.ids) {
          if (!nextOriginal.done && nextOriginal.value < id) fail();
          if (!nextOriginal.done && nextOriginal.value === id) nextOriginal = original.next();
        }
        if (verifier.complete && !nextOriginal.done) fail();
        for (const source of SOURCES.filter(value => !['auth', 'community'].includes(value))) {
          within();
          const result = await clients[source].importRecoveryScope({ restoreId, recoveryId, originalScopeDigest: group.scopeDigest, entry, page });
          validateScopeReceipt(recoveryScopeEnvelope(result, 'scope', restoreId, recoveryId, group.scopeDigest),
            entry, group.kind, expected, verifier.complete);
        }
      } while (!verifier.complete);
      const result = verifier.result();
      if (result.after !== expected.after) fail();
    }
  }
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
  return { schemaVersion: 1, recoveryId, restoreId, environment: repository.environment, status: 'authority-receipts-verified',
    authority, checkpointId: selected.manifest.checkpointId, participants, invalidations,
    isolationVerified: false, publicCutoverAllowed: false };
}
