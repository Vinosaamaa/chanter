import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { exactFields, environmentName, MAX_PAGE_BYTES } from './terminal-journal.mjs';
import { SOURCES } from './terminal-journal-recovery.mjs';

/** Container execution is transport only; callers must validate every returned protocol receipt. */
export function lifecycleClient({ source, environment, composeFile, project = `chanter-${environment}`, execute = execFileSync }) {
  environmentName(environment);
  if (!SOURCES.includes(source) || !path.isAbsolute(composeFile)
      || !/^(?:chanter-(?:staging|production)|chanter-recovery-[a-f0-9]{32})$/.test(project))
    throw new Error('Invalid private lifecycle execution target');
  const invoke = (operation, body = undefined, args = []) => {
    const input = body === undefined ? '' : JSON.stringify(body);
    if (Buffer.byteLength(input) > (operation === 'reapply' ? MAX_PAGE_BYTES : 2048))
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
    reapply: async value => invoke('reapply', value), receipt: async () => invoke('receipt'),
    invalidate: async value => {
      if (!['auth', 'agent'].includes(source)) throw new Error('Source has no recovery invalidation route');
      return invoke('invalidate', value);
    } });
}
