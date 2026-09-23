import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { isDeepStrictEqual } from 'node:util';
import { configurationBackupEnvironment, verifiedResticTool } from './configuration-backup.mjs';
import { environmentName, exactFields, nonzeroUuid, validateWatermark } from './terminal-journal.mjs';
import { InventoryVerifier, inventorySnapshot, inventoryObject, inventoryRestorable, MAX_INVENTORY_PAGE_BYTES } from './resource-inventory.mjs';

export const MAX_RESOURCE_BYTES = 10 * 1024 * 1024;
const HEX = /^[a-f0-9]{64}$/;
const fail = () => { throw new Error('Resource object archive verification failed'); };
const digest = bytes => createHash('sha256').update(bytes).digest('hex');

/** Private normalized inventory tuple, not a new source API or resource catalogue. */
function inventoryEntry(value) {
  exactFields(value, ['resourceId', 'courseId', 'key', 'byteSize', 'sha256', 'providerVersionId', 'disposition', 'storageWriteSettled']);
  nonzeroUuid(value.resourceId); nonzeroUuid(value.courseId);
  if (typeof value.key !== 'string' || value.key.length > 150) fail();
  const parts = value.key.split('/');
  if (parts.length !== 5 || parts[0] !== 'resources' || parts[1] !== 'v1'
      || parts[2] !== value.courseId || parts[3] !== value.resourceId) fail();
  nonzeroUuid(parts[4]);
  if (!Number.isSafeInteger(value.byteSize) || value.byteSize < 1 || value.byteSize > MAX_RESOURCE_BYTES
      || typeof value.sha256 !== 'string' || !HEX.test(value.sha256) || value.providerVersionId !== null
      || value.disposition !== 'EXTANT' || value.storageWriteSettled !== true) fail();
  return { resourceId: value.resourceId, courseId: value.courseId, key: value.key, byteSize: value.byteSize,
    sha256: value.sha256, providerVersionId: null, disposition: 'EXTANT', storageWriteSettled: true };
}

function maintenanceCheckpoint(value) {
  exactFields(value, ['inventoryId', 'storageNamespaceSha256', 'writers', 'unsettledWrites', 'authority']);
  nonzeroUuid(value.inventoryId); validateWatermark(value.authority);
  if (typeof value.storageNamespaceSha256 !== 'string' || !HEX.test(value.storageNamespaceSha256)
      || value.writers !== 'QUIESCENT' || value.unsettledWrites !== 0) fail();
  return value;
}

export function resourceBackupEnvironment(settings, environment) {
  environmentName(environment);
  const base = configurationBackupEnvironment(settings, environment), password = settings.CHANTER_RESOURCE_BACKUP_PASSWORD;
  if (typeof password !== 'string' || Buffer.byteLength(password) < 32 || /[\r\n\0]/.test(password)
      || [settings.CHANTER_BACKUP_CIPHER_PASS, settings.CHANTER_CONFIG_BACKUP_PASSWORD,
        settings.CHANTER_TERMINAL_JOURNAL_PASSWORD].includes(password)) fail();
  return { ...base, RESTIC_PASSWORD: password,
    RESTIC_REPOSITORY: `s3:${new URL(settings.CHANTER_BACKUP_S3_ENDPOINT).origin}/${settings.CHANTER_BACKUP_S3_BUCKET}/resource-objects/${environment}` };
}

/** Byte evidence only. The owning adapter must prove current inventory and writer fencing before using this leaf. */
export class ResourceObjectArchive {
  #tool; #execute;
  constructor({ bundleDir, environment, env, kind = 'remote', execute = execFileSync }) {
    this.environment = environmentName(environment);
    if (!['remote', 'fixture'].includes(kind) || typeof env.RESTIC_PASSWORD !== 'string' || !env.RESTIC_PASSWORD
        || (kind === 'fixture' && !path.isAbsolute(env.RESTIC_REPOSITORY ?? ''))) fail();
    if (kind === 'remote') {
      let uri;
      try { uri = new URL(env.RESTIC_REPOSITORY?.slice(3)); } catch { fail(); }
      if (!env.RESTIC_REPOSITORY.startsWith('s3:https://') || uri.protocol !== 'https:' || uri.username || uri.password
          || uri.search || uri.hash || !uri.pathname.endsWith(`/resource-objects/${environment}`)
          || Buffer.byteLength(env.RESTIC_PASSWORD) < 32) fail();
    }
    this.kind = kind;
    try {
      this.#tool = verifiedResticTool(bundleDir, { ...env, GOMAXPROCS: '1', GOMEMLIMIT: '256MiB' },
        process.platform === 'win32' ? 'restic.exe' : 'restic');
    } catch { fail(); }
    this.#execute = execute;
    Object.freeze(this);
  }

  #run(args, input, maxBuffer = 1024 * 1024) {
    try {
      const output = this.#execute(this.#tool.executable, ['--no-cache', ...args], { input, encoding: null,
        env: this.#tool.env, timeout: 60_000, maxBuffer, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
      if (!Buffer.isBuffer(output)) fail();
      return output;
    } catch { fail(); }
  }

  initialize() { this.#run(['init', '--repository-version', '2']); }

  #saveInventoryJson(value, name, attempt) {
    const bytes = Buffer.from(JSON.stringify(value));
    if (bytes.length > MAX_INVENTORY_PAGE_BYTES) fail();
    let summaries;
    try { summaries = this.#run(['backup', '--stdin', '--stdin-filename', name, '--host', `chanter-${this.environment}`,
      '--tag', 'resource-inventory-v1', '--tag', attempt.tag, '--json'], bytes).toString('utf8').trim().split(/\r?\n/)
      .map(line => JSON.parse(line)).filter(row => row.message_type === 'summary'); }
    catch { attempt.ambiguous=true;fail(); }
    if (summaries.length !== 1 || !HEX.test(summaries[0].snapshot_id ?? '')
        || attempt.saved.has(summaries[0].snapshot_id)) {attempt.ambiguous=true;fail();}
    const reference = { snapshotId: summaries[0].snapshot_id, byteSize: bytes.length, sha256: digest(bytes) };
    attempt.saved.set(reference.snapshotId,name);
    if (!isDeepStrictEqual(this.#readInventoryJson(reference, name), value)) fail();
    return reference;
  }
  #unpublishedFailure(attempt) {
    let status=attempt.saved.size===0&&!attempt.ambiguous?'NONE':'PENDING';
    if(!attempt.ambiguous&&attempt.saved.size>0) try {
      const list=()=>{
        const rows=JSON.parse(this.#run(['snapshots','--json','--tag',attempt.tag],undefined,4*1024*1024).toString('utf8'));
        if(!Array.isArray(rows)||rows.length>2049)fail();return rows;
      };
      const rows=list(), ids=new Set();
      if(rows.length!==attempt.saved.size)fail();
      for(const row of rows) {
        const name=attempt.saved.get(row.id);
        if(!name||ids.has(row.id)||row.hostname!==`chanter-${this.environment}`
            ||!isDeepStrictEqual(row.paths,[path.join(path.parse(process.cwd()).root,name)])||!Array.isArray(row.tags)
            ||row.tags.length!==2||!row.tags.includes('resource-inventory-v1')||!row.tags.includes(attempt.tag))fail();
        ids.add(row.id);
      }
      // Exact IDs only. No leaf, published attempt, retention policy or prune is involved.
      const owned=[...ids];
      for(let offset=0;offset<owned.length;offset+=128)this.#run(['forget',...owned.slice(offset,offset+128)]);
      if(list().length!==0)fail();
      status='FORGOTTEN';
    }catch { /* Repository attempt tags and opaque diagnostics preserve unresolved ownership. */ }
    const error=new Error('Resource inventory publication failed');
    error.cleanup=Object.freeze({attemptId:attempt.id,status,snapshotIds:Object.freeze([...attempt.saved.keys()])});
    return error;
  }
  #readInventoryJson(reference, name) {
    exactFields(reference, ['snapshotId', 'byteSize', 'sha256']);
    if (!HEX.test(reference.snapshotId ?? '') || !HEX.test(reference.sha256 ?? '')
        || !Number.isSafeInteger(reference.byteSize) || reference.byteSize < 1 || reference.byteSize > MAX_INVENTORY_PAGE_BYTES) fail();
    const bytes = this.#run(['dump', reference.snapshotId, name], undefined, reference.byteSize + 1);
    if (bytes.length !== reference.byteSize || digest(bytes) !== reference.sha256) fail();
    try { return JSON.parse(bytes.toString('utf8')); } catch { fail(); }
  }

  /** The caller owns source/provider closure. No manifest is returned for a partial inventory. */
  publishInventory(snapshot, maintenance, readPage, objectReference) {
    const expected = inventorySnapshot(snapshot), checkpoint = maintenanceCheckpoint(maintenance);
    if (checkpoint.inventoryId !== expected.inventoryId || checkpoint.storageNamespaceSha256 !== expected.namespaceSha256
        || !isDeepStrictEqual(checkpoint.authority, expected.authority) || typeof readPage !== 'function'
        || typeof objectReference !== 'function') fail();
    const id=randomUUID(),attempt={id,tag:`resource-inventory-attempt-${id}`,saved:new Map(),ambiguous:false};
    try {
      const verifier = new InventoryVerifier(expected), pages = [];
      do {
        // Fixed size bounds both source parsing and the encrypted page including byte references.
        const page = structuredClone(readPage(verifier.after, 128));
        verifier.accept(page);
        const objects = page.references.map(reference => {
          if (!inventoryRestorable(reference)) return null;
          const archived = structuredClone(objectReference(reference));
          if (archived?.inventoryId !== expected.inventoryId || !isDeepStrictEqual(archived.authority, expected.authority)) fail();
          this.readVerified(archived, inventoryObject(reference), expected.namespaceSha256);
          return archived;
        });
        if (pages.length >= 2048) fail();
        pages.push(this.#saveInventoryJson({ schemaVersion: 1, page, objects }, 'resource-inventory-page.json',attempt));
      } while (!verifier.complete);
      const current = structuredClone(readPage(expected.referenceCount, 1));
      this.#requireInventoryEnd(current, expected);
      const manifest = this.#saveInventoryJson({ schemaVersion: 1, repositoryKind: this.kind, environment: this.environment,
        snapshot: expected, pages, publicCutoverAllowed: false }, 'resource-inventory.json',attempt);
      const reference = Object.freeze({ schemaVersion: 1, repositoryKind: this.kind, environment: this.environment,
        snapshot: expected, manifest, publicCutoverAllowed: false });
      this.verifyInventory(reference, expected);
      this.#requireInventoryEnd(structuredClone(readPage(expected.referenceCount, 1)), expected);
      return reference;
    }catch {throw this.#unpublishedFailure(attempt);}
  }
  #requireInventoryEnd(page, snapshot) {
    exactFields(page, ['schemaVersion', 'snapshot', 'after', 'references', 'nextAfter']);
    if (page.schemaVersion !== 1 || !isDeepStrictEqual(inventorySnapshot(page.snapshot), snapshot)
        || page.after !== snapshot.referenceCount || !Array.isArray(page.references) || page.references.length !== 0
        || page.nextAfter !== null) fail();
  }

  /** Full source-chain and byte verification precedes delivery of any archived reference. */
  verifyInventory(reference, snapshot, acceptReference = null) {
    const expected = inventorySnapshot(snapshot);
    exactFields(reference, ['schemaVersion', 'repositoryKind', 'environment', 'snapshot', 'manifest', 'publicCutoverAllowed']);
    if (reference.schemaVersion !== 1 || reference.repositoryKind !== this.kind || reference.environment !== this.environment
        || reference.publicCutoverAllowed !== false || !isDeepStrictEqual(reference.snapshot, expected)
        || (acceptReference !== null && typeof acceptReference !== 'function')) fail();
    const manifest = this.#readInventoryJson(reference.manifest, 'resource-inventory.json');
    exactFields(manifest, ['schemaVersion', 'repositoryKind', 'environment', 'snapshot', 'pages', 'publicCutoverAllowed']);
    if (manifest.schemaVersion !== 1 || manifest.repositoryKind !== this.kind || manifest.environment !== this.environment
        || manifest.publicCutoverAllowed !== false || !isDeepStrictEqual(manifest.snapshot, expected)
        || !Array.isArray(manifest.pages) || manifest.pages.length < 1 || manifest.pages.length > 2048) fail();
    const verifier = new InventoryVerifier(expected);
    for (const stored of manifest.pages) {
      const envelope = this.#readInventoryJson(stored, 'resource-inventory-page.json');
      exactFields(envelope, ['schemaVersion', 'page', 'objects']);
      if (envelope.schemaVersion !== 1 || !Array.isArray(envelope.objects)
          || envelope.objects.length !== envelope.page?.references?.length) fail();
      verifier.accept(envelope.page);
      for (let index = 0; index < envelope.page.references.length; index++) {
        const source = envelope.page.references[index], object = envelope.objects[index];
        if (!inventoryRestorable(source)) { if (object !== null) fail(); continue; }
        if (object?.inventoryId !== expected.inventoryId || !isDeepStrictEqual(object.authority, expected.authority)) fail();
        this.readVerified(object, inventoryObject(source), expected.namespaceSha256);
      }
    }
    if (!verifier.complete) fail();
    if (acceptReference) for (const stored of manifest.pages) {
      const envelope = this.#readInventoryJson(stored, 'resource-inventory-page.json');
      envelope.page.references.forEach((source, index) => acceptReference(source, envelope.objects[index]));
    }
    return Object.freeze({ schemaVersion: 1, inventoryId: expected.inventoryId, databaseBackupId: expected.databaseBackupId,
      referenceCount: expected.referenceCount, referenceDigest: expected.referenceDigest, publicCutoverAllowed: false });
  }

  capture(entry, maintenance, content) {
    const object = inventoryEntry(entry), checkpoint = maintenanceCheckpoint(maintenance);
    if (!Buffer.isBuffer(content) || content.length !== object.byteSize || content.length > MAX_RESOURCE_BYTES) fail();
    // Bound and copy one binary object. No text conversion or plaintext filesystem spool is involved.
    const bytes = Buffer.from(content);
    if (digest(bytes) !== object.sha256) fail();
    let snapshotId;
    try {
      const summaries = this.#run(['backup', '--stdin', '--stdin-filename', 'resource-object.bin',
        '--host', `chanter-${this.environment}`, '--tag', 'resource-object-v1', '--json'], bytes)
        .toString('utf8').trim().split(/\r?\n/).map(line => JSON.parse(line)).filter(row => row.message_type === 'summary');
      if (summaries.length !== 1 || !HEX.test(summaries[0].snapshot_id ?? '')) fail();
      snapshotId = summaries[0].snapshot_id;
    } catch { fail(); }
    const reference = Object.freeze({ schemaVersion: 1, repositoryKind: this.kind, environment: this.environment,
      inventoryId: checkpoint.inventoryId, storageNamespaceSha256: checkpoint.storageNamespaceSha256,
      authority: Object.freeze({ ...checkpoint.authority }), object: Object.freeze(object), snapshotId, publicCutoverAllowed: false });
    // Do not return a usable reference until the complete decrypted bytes match the canonical tuple.
    this.readVerified(reference, object, checkpoint.storageNamespaceSha256);
    return reference;
  }

  readVerified(reference, currentEntry, storageNamespaceSha256) {
    const expected = inventoryEntry(currentEntry);
    exactFields(reference, ['schemaVersion', 'repositoryKind', 'environment', 'inventoryId', 'storageNamespaceSha256',
      'authority', 'object', 'snapshotId', 'publicCutoverAllowed']);
    nonzeroUuid(reference.inventoryId); validateWatermark(reference.authority);
    if (reference.schemaVersion !== 1 || reference.repositoryKind !== this.kind || reference.environment !== this.environment
        || typeof storageNamespaceSha256 !== 'string' || !HEX.test(storageNamespaceSha256)
        || reference.storageNamespaceSha256 !== storageNamespaceSha256 || reference.publicCutoverAllowed !== false
        || !HEX.test(reference.snapshotId ?? '') || !isDeepStrictEqual(inventoryEntry(reference.object), expected)) fail();
    const bytes = this.#run(['dump', reference.snapshotId, 'resource-object.bin'], undefined, expected.byteSize + 1);
    if (bytes.length !== expected.byteSize || digest(bytes) !== expected.sha256) fail();
    return bytes;
  }
}
