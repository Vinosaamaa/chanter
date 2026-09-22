import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { configurationBackupEnvironment, verifiedResticTool } from './configuration-backup.mjs';
import { environmentName, MAX_PAGE_BYTES, validateWatermark } from './terminal-journal.mjs';
import { MAX_SCOPE_BYTES } from './deleted-scope.mjs';

export const MAX_MANIFEST_BYTES = 1024 * 1024;
export const MAX_MANIFESTS = 20_000;
const ID = /^[a-f0-9]{64}$/;
const kinds = new Set(['page', 'scope', 'manifest']);
const maximumBytes = kind => kind === 'page' ? MAX_PAGE_BYTES : kind === 'scope' ? MAX_SCOPE_BYTES : MAX_MANIFEST_BYTES;

export function journalBackupEnvironment(settings, environment) {
  const base = configurationBackupEnvironment(settings, environment);
  const password = settings.CHANTER_TERMINAL_JOURNAL_PASSWORD;
  if (typeof password !== 'string' || Buffer.byteLength(password) < 32 || /[\r\n\0]/.test(password)
      || [settings.CHANTER_BACKUP_CIPHER_PASS, settings.CHANTER_CONFIG_BACKUP_PASSWORD].includes(password)) {
    throw new Error('Terminal journal requires a separate encryption password of at least 32 bytes');
  }
  return { ...base, RESTIC_PASSWORD: password,
    RESTIC_REPOSITORY: `s3:${new URL(settings.CHANTER_BACKUP_S3_ENDPOINT).origin}/${settings.CHANTER_BACKUP_S3_BUCKET}/terminal-journal/${environment}` };
}

/** Restic owns encryption and persistence. Fixture repositories cannot be mistaken for remote storage evidence. */
export class JournalRepository {
  #tool; #execute;
  constructor({ bundleDir, environment, env, kind = 'remote', execute = execFileSync }) {
    this.environment = environmentName(environment);
    if (!['remote', 'fixture'].includes(kind)
        || (kind === 'remote' && (!env.RESTIC_REPOSITORY?.startsWith('s3:https://')
          || !env.RESTIC_REPOSITORY.endsWith(`/terminal-journal/${environment}`)))
        || (kind === 'fixture' && !path.isAbsolute(env.RESTIC_REPOSITORY ?? ''))) throw new Error('Invalid terminal journal repository');
    this.kind = kind;
    this.#tool = verifiedResticTool(bundleDir, env, process.platform === 'win32' ? 'restic.exe' : 'restic');
    this.#execute = execute;
    Object.freeze(this);
  }
  #run(args, input, maximum = MAX_MANIFEST_BYTES) {
    try {
      return this.#execute(this.#tool.executable, ['--no-cache', ...args], { input, encoding: 'utf8',
        timeout: 60000, maxBuffer: maximum, env: this.#tool.env, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    } catch { throw new Error('Encrypted terminal journal storage failed'); }
  }
  initialize() { this.#run(['init', '--repository-version', '2']); }
  write(kind, value) {
    if (!kinds.has(kind)) throw new Error('Invalid terminal journal object kind');
    const input = JSON.stringify(value), maximum = maximumBytes(kind);
    if (typeof input !== 'string' || Buffer.byteLength(input) > maximum) throw new Error('Terminal journal object exceeds bounded size');
    const tags = ['--tag', `terminal-journal-${kind}-v1`];
    if (kind === 'manifest') {
      validateWatermark(value.authority);
      tags.push('--tag', `revision-${value.authority.revision}`, '--tag', `digest-${value.authority.digest}`);
    }
    const output = this.#run(['backup', '--stdin', '--stdin-filename', `terminal-${kind}.json`,
      '--host', `chanter-${this.environment}`, ...tags, '--json'], input);
    try {
      const summary = output.trim().split(/\r?\n/).map(line => JSON.parse(line)).find(row => row.message_type === 'summary');
      if (!ID.test(summary?.snapshot_id ?? '')) throw new Error();
      return summary.snapshot_id;
    } catch { throw new Error('Encrypted terminal journal storage returned no immutable snapshot'); }
  }
  read(kind, snapshotId) {
    if (!kinds.has(kind) || !ID.test(snapshotId ?? '')) throw new Error('Invalid terminal journal snapshot reference');
    const output = this.#run(['dump', snapshotId, `terminal-${kind}.json`], undefined, maximumBytes(kind));
    try { return JSON.parse(output); }
    catch { throw new Error('Encrypted terminal journal storage returned invalid JSON'); }
  }
  manifests() {
    const output = this.#run(['snapshots', '--json', '--host', `chanter-${this.environment}`,
      '--tag', 'terminal-journal-manifest-v1'], undefined, 16 * 1024 * 1024);
    try {
      const rows = JSON.parse(output);
      if (!Array.isArray(rows) || rows.length > MAX_MANIFESTS) throw new Error();
      return rows.map(row => {
        if (!ID.test(row.id ?? '') || !Array.isArray(row.tags) || row.hostname !== `chanter-${this.environment}`
            || !row.tags.includes('terminal-journal-manifest-v1')) throw new Error();
        const revisions = row.tags.filter(tag => /^revision-/.test(tag)), digests = row.tags.filter(tag => /^digest-/.test(tag));
        if (revisions.length !== 1 || digests.length !== 1 || !/^revision-(0|[1-9][0-9]*)$/.test(revisions[0])) throw new Error();
        const authority = validateWatermark({ revision: Number(revisions[0].slice(9)), digest: digests[0].slice(7) });
        return { snapshotId: row.id, authority };
      });
    } catch { throw new Error('Encrypted terminal journal snapshot listing is invalid or exceeds capacity'); }
  }
}
