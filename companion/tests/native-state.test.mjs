import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { chmod, mkdir, mkdtemp } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';
import { NativeState } from '../src/native-state.mjs';

async function statePath() {
  await mkdir('.cache/companion-state', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-state/run-'));
  return path.join(parent, 'protected');
}

test('native installation identity and consumed request survive reopening protected state', async () => {
  const location = await statePath();
  const first = await NativeState.open(location);
  const installationId = first.installationId;
  first.consume('request-316', Date.now() + 60_000);
  first.close();
  const reopened = await NativeState.open(location);
  try {
    assert.equal(reopened.installationId, installationId);
    assert.throws(() => reopened.consume('request-316', Date.now() + 60_000), { code: 'CAPABILITY_ALREADY_USED' });
  } finally { reopened.close(); }
});

test('pairing handles bind exact origin/user/session and re-pairing or logout revokes them', async () => {
  const state = await NativeState.open(await statePath());
  const binding = { origin: 'https://chanter.example', userId: 'learner-316', sessionId: 'session-316' };
  try {
    const first = state.pair({ ...binding, expiresAt: Date.now() + 60_000 });
    state.requirePairing(first.handle, binding);
    for (const invalid of [{ ...binding, origin: 'https://attacker.example' }, { ...binding, userId: 'other' },
      { ...binding, sessionId: 'other' }]) assert.throws(() => state.requirePairing(first.handle, invalid), { code: 'PAIRING_REQUIRED' });
    const second = state.pair({ ...binding, expiresAt: Date.now() + 60_000 });
    assert.throws(() => state.requirePairing(first.handle, binding), { code: 'PAIRING_REQUIRED' });
    state.requirePairing(second.handle, binding);
    state.revokePairing();
    assert.throws(() => state.requirePairing(second.handle, binding), { code: 'PAIRING_REQUIRED' });
  } finally { state.close(); }
});

test('two independent state connections cannot consume the same request', async () => {
  const location = await statePath();
  const first = await NativeState.open(location); const second = await NativeState.open(location);
  try {
    first.consume('one-attempt', Date.now() + 60_000);
    assert.throws(() => second.consume('one-attempt', Date.now() + 60_000), { code: 'CAPABILITY_ALREADY_USED' });
    second.consume('different-attempt', Date.now() + 60_000);
  } finally { first.close(); second.close(); }
});

test('state with broadened filesystem access fails closed instead of silently repairing it', async () => {
  const location = await statePath();
  const state = await NativeState.open(location); state.close();
  if (process.platform === 'win32') {
    await promisify(execFile)(path.join(process.env.SystemRoot, 'System32', 'icacls.exe'),
      [location, '/grant', '*S-1-1-0:(OI)(CI)(R)'], { windowsHide: true });
  } else await chmod(location, 0o755);
  await assert.rejects(NativeState.open(location), { code: 'NATIVE_STATE_UNPROTECTED' });
});
