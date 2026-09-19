import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { lstat, mkdir, readFile, readdir, realpath, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { CompanionError } from './codex-app-server.mjs';

const samePath = (a, b) => process.platform === 'win32' ? a.toLowerCase() === b.toLowerCase() : a === b;
const alive = (pid) => { if (pid === 0) return false; try { process.kill(pid, 0); return true; } catch (error) { return error.code !== 'ESRCH'; } };
const blocked = () => new CompanionError('NATIVE_RETENTION_BLOCKED');

async function directoryAt(root, directory) {
  if (!path.isAbsolute(root) || !samePath(await realpath(root), path.resolve(root))
      || !path.relative(root, directory) || path.relative(root, directory).startsWith('..')
      || path.isAbsolute(path.relative(root, directory)) || !samePath(await realpath(directory), path.resolve(directory))
      || !(await lstat(directory)).isDirectory() || (await lstat(directory)).isSymbolicLink()) throw blocked();
}

async function removeVerifiedTree(root, directory) {
  // Verify the resolved absolute target and every descendant before recursive deletion.
  await directoryAt(root, directory);
  let count = 0;
  async function inspect(current, depth) {
    if (++count > 4096 || depth > 16) throw blocked();
    const stat = await lstat(current);
    if (stat.isSymbolicLink() || (!stat.isDirectory() && !stat.isFile())) throw blocked();
    if (stat.isDirectory()) for (const name of await readdir(current)) await inspect(path.join(current, name), depth + 1);
  }
  await inspect(directory, 0);
  await rm(directory, { recursive: true, maxRetries: 2, retryDelay: 50 });
}

export async function createRun(root, installationId) {
  try {
    const runs = path.join(root, 'runs');
    await mkdir(runs, { mode: 0o700 }).catch((error) => { if (error.code !== 'EEXIST') throw error; });
    await directoryAt(root, runs);
    const directory = path.join(runs, randomUUID());
    await mkdir(directory, { mode: 0o700 });
    const owner = { version: 1, installationId, controllerPid: process.pid, providerPid: 0 };
    await writeFile(path.join(directory, '.owner.json'), JSON.stringify(owner), { flag: 'wx', mode: 0o600 });
    for (const name of ['empty', 'sqlite', 'log', 'home', 'tmp']) await mkdir(path.join(directory, name), { mode: 0o700 });
    return { directory, owner };
  } catch { throw blocked(); }
}

/** Record null before spawn so a crash in the spawn/record gap requires operator review. */
export function markProvider(run, pid) {
  if (pid !== null && (!Number.isSafeInteger(pid) || pid < 0)) throw blocked();
  run.owner.providerPid = pid;
  writeFileSync(path.join(run.directory, '.owner.json'), JSON.stringify(run.owner), { mode: 0o600 });
}

async function readOwner(root, directory, installationId) {
  await directoryAt(root, directory);
  const marker = path.join(directory, '.owner.json');
  const stat = await lstat(marker);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 1024) throw blocked();
  const owner = JSON.parse(await readFile(marker, 'utf8'));
  if (owner.version !== 1 || owner.installationId !== installationId || !Number.isSafeInteger(owner.controllerPid)
      || owner.controllerPid <= 0 || !Number.isSafeInteger(owner.providerPid) || owner.providerPid < 0) throw blocked();
  return owner;
}

export async function finishRun(root, run, installationId) {
  try {
    const owner = await readOwner(root, run.directory, installationId);
    if (owner.controllerPid !== process.pid || alive(owner.providerPid)) throw blocked();
    await removeVerifiedTree(root, run.directory);
  } catch { throw blocked(); }
}

export async function recoverRuns(root, installationId) {
  try {
    const runs = path.join(root, 'runs');
    await mkdir(runs, { mode: 0o700 }).catch((error) => { if (error.code !== 'EEXIST') throw error; });
    await directoryAt(root, runs);
    for (const name of await readdir(runs)) {
      if (!/^[a-f0-9-]{36}$/.test(name)) throw blocked();
      const directory = path.join(runs, name);
      const owner = await readOwner(root, directory, installationId);
      if (alive(owner.controllerPid) || alive(owner.providerPid)) throw blocked();
      await removeVerifiedTree(root, directory);
    }
  } catch { throw blocked(); }
}

/** Provider-created executable wrappers, separate from its authentication store. No auth files are read or deleted. */
export async function removeProviderTemporary(root) {
  const directory = path.join(root, 'provider', 'tmp');
  try { await lstat(directory); } catch (error) { if (error.code === 'ENOENT') return; throw blocked(); }
  try { await removeVerifiedTree(root, directory); } catch { throw blocked(); }
}
