import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { isDeepStrictEqual } from 'node:util';
import { configurationBackupEnvironment, verifiedResticTool } from './configuration-backup.mjs';
import { environmentName, exactFields, nonzeroUuid, validateWatermark } from './terminal-journal.mjs';

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

/** Byte evidence only. The future owning adapter must prove current inventory and writer fencing before using this leaf. */
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
