import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { exactFields, environmentName, MAX_PAGE_BYTES, nonzeroUuid, validateWatermark } from './terminal-journal.mjs';
import { SOURCES } from './terminal-journal-recovery.mjs';
import { START, SCOPE_KINDS, MAX_SCOPE_BYTES } from './deleted-scope.mjs';

/** Container execution is transport only; callers must validate every returned protocol receipt. */
export function lifecycleClient({ source, environment, composeFile, project = `chanter-${environment}`, execute = execFileSync }) {
  environmentName(environment);
  if (!SOURCES.includes(source) || !path.isAbsolute(composeFile)
      || !/^(?:chanter-(?:staging|production)|chanter-recovery-[a-f0-9]{32})$/.test(project))
    throw new Error('Invalid private lifecycle execution target');
  const invoke = (operation, body = undefined, args = [], raw = false) => {
    const input = raw ? body : body === undefined ? '' : JSON.stringify(body);
    const limit = operation === 'reapply' ? MAX_PAGE_BYTES
      : ['scope-import', 'scope-recovery-import'].includes(operation) ? MAX_SCOPE_BYTES + 37
      : ['scope-derive', 'scope-recovery-read'].includes(operation) ? 4096 + 37 : 2048;
    if (Buffer.byteLength(input) > limit)
      throw new Error('Private lifecycle request exceeds bounded size');
    let output;
    try {
      output = execute('docker', ['compose', '--project-name', project, '-f', composeFile, 'exec', '-T', `${source}-service`,
        'java', '-Xms8m', '-Xmx32m', '-XX:MaxMetaspaceSize=48m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1',
        '-cp', '/app/helpers', 'Lifecycle', operation, ...args],
      { input, encoding: 'utf8', timeout: 30_000, maxBuffer: MAX_PAGE_BYTES, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    } catch { throw new Error('Private lifecycle helper execution failed'); }
    try { return JSON.parse(output); }
    catch { throw new Error('Private lifecycle helper returned invalid JSON'); }
  };
  const scopePost = (operation, value, maximum) => {
    nonzeroUuid(value.entry?.targetId);
    const body = JSON.stringify(value);
    if (Buffer.byteLength(body) > maximum) throw new Error('Private scope request exceeds bounded size');
    return invoke(operation, value.entry.targetId + '\n' + body, [], true);
  };
  const auth = () => { if (source !== 'auth') throw new Error('Journal export requires auth'); };
  return Object.freeze({ kind: 'remote',
    checkpoint: async () => { auth(); const value = invoke('checkpoint-get'); exactFields(value, ['checkpoint']); return value.checkpoint; },
    page: async (after, through) => {
      auth();
      if (!Number.isSafeInteger(after) || after < 0 || (through !== null && (!Number.isSafeInteger(through) || through < after)))
        throw new Error('Invalid journal page range');
      return invoke('export', undefined, [String(after), through === null ? '-' : String(through)]);
    },
    acknowledge: async value => { auth(); return invoke('checkpoint-put', value); },
    scope: async (entry, kind, after) => {
      if (source !== 'community' || !SCOPE_KINDS.includes(kind)) throw new Error('Invalid private scope source');
      nonzeroUuid(entry.targetId); nonzeroUuid(entry.eventId);
      validateWatermark({ revision: entry.revision, digest: entry.digest });
      if (entry.revision < 1) throw new Error('Invalid private scope authority');
      if (after !== START) nonzeroUuid(after);
      return invoke('scope-read', [entry.targetId, String(entry.revision), entry.eventId, entry.digest, kind, after, ''].join('\n'), [], true);
    },
    importScope: async value => {
      if (source === 'auth') throw new Error('Source has no scope import');
      return scopePost('scope-import', value, MAX_SCOPE_BYTES);
    },
    deriveScope: async value => {
      if (source !== 'community') throw new Error('Only community derives restored relationships');
      return scopePost('scope-derive', value, 4096);
    },
    recoveryScope: async value => {
      if (source !== 'community') throw new Error('Only community exports restored relationships');
      return scopePost('scope-recovery-read', value, 4096);
    },
    importRecoveryScope: async value => {
      if (['auth', 'community'].includes(source)) throw new Error('Source has no derived scope import');
      return scopePost('scope-recovery-import', value, MAX_SCOPE_BYTES);
    },
    reapply: async value => invoke('reapply', value), receipt: async () => invoke('receipt'),
    invalidate: async value => {
      if (!['auth', 'agent'].includes(source)) throw new Error('Source has no recovery invalidation route');
      return invoke('invalidate', value);
    } });
}
