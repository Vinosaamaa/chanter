import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, readdir, symlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { createRun, finishRun, recoverRuns } from '../src/native-retention.mjs';
import { NativeState } from '../src/native-state.mjs';

async function privateRoot() {
  await mkdir('.cache/companion-retention', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-retention/run-'));
  const root = path.join(parent, 'protected');
  (await NativeState.open(root)).close();
  return root;
}

test('retention removes only the owned completed run and preserves provider state', async () => {
  const root = await privateRoot();
  await mkdir(path.join(root, 'provider'));
  await writeFile(path.join(root, 'provider', 'synthetic-preserve'), 'provider-owned test marker');
  const run = await createRun(root, 'installation-fixture');
  await writeFile(path.join(run.directory, 'sqlite', 'synthetic-content'), 'synthetic course text');
  await finishRun(root, run, 'installation-fixture');
  assert.deepEqual(await readdir(path.join(root, 'runs')), []);
  assert.deepEqual(await readdir(path.join(root, 'provider')), ['synthetic-preserve']);
});

test('recovery removes an owned orphan only after its recorded controller and provider are absent', async () => {
  const root = await privateRoot();
  const run = await createRun(root, 'installation-fixture');
  const child = spawn(process.execPath, ['-e', ''], { windowsHide: true, stdio: 'ignore' });
  await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
  run.owner.controllerPid = child.pid;
  await writeFile(path.join(run.directory, '.owner.json'), JSON.stringify(run.owner));
  await recoverRuns(root, 'installation-fixture');
  assert.deepEqual(await readdir(path.join(root, 'runs')), []);
});

test('recovery refuses active or unrecognized directories instead of deleting them', async () => {
  const root = await privateRoot();
  await createRun(root, 'installation-fixture');
  await assert.rejects(recoverRuns(root, 'installation-fixture'), { code: 'NATIVE_RETENTION_BLOCKED' });
});

test('retention refuses a linked descendant and preserves the outside canary', async () => {
  const root = await privateRoot();
  const outside = path.join(path.dirname(root), 'outside');
  await mkdir(outside); await writeFile(path.join(outside, 'canary'), 'preserved');
  const run = await createRun(root, 'installation-fixture');
  await symlink(outside, path.join(run.directory, 'linked'), process.platform === 'win32' ? 'junction' : 'dir');
  await assert.rejects(finishRun(root, run, 'installation-fixture'), { code: 'NATIVE_RETENTION_BLOCKED' });
  assert.equal(await readFile(path.join(outside, 'canary'), 'utf8'), 'preserved');
});
