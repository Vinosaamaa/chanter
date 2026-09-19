import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { mkdir, mkdtemp } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { NativeState } from '../src/native-state.mjs';
import { startNativeServer } from '../src/native-server.mjs';

test('actual loopback listener rejects hostile origins and has no browser pairing or generic RPC route', async () => {
  await mkdir('.cache/companion-state', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-state/http-'));
  const state = await NativeState.open(path.join(parent, 'protected'));
  const { publicKey } = generateKeyPairSync('ed25519');
  let calls = 0;
  const server = await startNativeServer({ state, origin: 'https://chanter.example', publicKey, port: 0,
    approve: async () => true, transport: async () => { calls++; } });
  try {
    for (const route of ['/pair', '/rpc', '/study']) {
      const response = await fetch(`http://127.0.0.1:${server.port}${route}`, {
        method: 'POST', headers: { origin: 'https://attacker.example', 'content-type': 'application/json' }, body: '{}' });
      assert.equal(response.status, 403);
      assert.equal(response.headers.get('access-control-allow-origin'), null);
    }
    const missingPair = await fetch(`http://127.0.0.1:${server.port}/study`, {
      method: 'POST', headers: { origin: 'https://chanter.example', 'content-type': 'application/json' }, body: '{}' });
    assert.equal(missingPair.status, 401);
    const absentPairRoute = await fetch(`http://127.0.0.1:${server.port}/pair`, {
      method: 'POST', headers: { origin: 'https://chanter.example', 'content-type': 'application/json' }, body: '{}' });
    assert.equal(absentPairRoute.status, 404);
    assert.equal(calls, 0);
  } finally { await server.close(); state.close(); }
});

test('actual HTTP stream requires signed native pairing and cannot replay a completed request', async () => {
  await mkdir('.cache/companion-state', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-state/http-'));
  const state = await NativeState.open(path.join(parent, 'protected'));
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  const origin = 'https://chanter.example';
  const ticket = (value) => {
    const body = Buffer.from(JSON.stringify(value)).toString('base64url');
    return body + '.' + sign(null, Buffer.from('chanter-native-v1.' + body), privateKey).toString('base64url');
  };
  let calls = 0;
  const answer = 'A'.repeat(32 * 1024);
  const server = await startNativeServer({ state, origin, publicKey, port: 0,
    approve: async () => true, transport: async (request, { onDelta }) => {
      calls++; onDelta(answer); return { text: answer, provenance: 'native-client-report' };
    } });
  try {
    const common = { version: 1, installationId: state.installationId, origin, userId: 'user-316', sessionId: 'session-316',
      issuedAt: Date.now(), expiresAt: Date.now() + 60_000 };
    const { handle } = await server.pair(ticket({ ...common, kind: 'pair', requestId: 'pair-316' }));
    const prompt = 'Synthetic evidence.';
    const body = JSON.stringify({ prompt, ticket: ticket({ ...common, kind: 'study', requestId: 'study-316', questionId: 'question-316',
      provider: 'codex', mode: 'quoted-evidence', model: 'gpt-6-astra', maxInputBytes: 4096, maxOutputBytes: 64 * 1024, deadlineMs: 1000,
      promptSha256: createHash('sha256').update(prompt).digest('hex'), evidenceSha256: [createHash('sha256').update(prompt).digest('hex')] }) });
    const headers = { origin, 'content-type': 'application/json', 'x-chanter-pairing': handle };
    const response = await fetch(`http://127.0.0.1:${server.port}/study`, { method: 'POST', headers, body });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('access-control-allow-origin'), origin);
    const events = await response.text();
    assert.equal((events.match(/event: delta/g) ?? []).length, 1);
    assert.equal((events.match(/event: completed/g) ?? []).length, 1);
    assert.ok(!events.includes('event: error'));
    const replay = await fetch(`http://127.0.0.1:${server.port}/study`, { method: 'POST', headers, body });
    assert.deepEqual(await replay.json(), { error: 'CAPABILITY_ALREADY_USED' });
    assert.equal(calls, 1);
  } finally { await server.close(); state.close(); }
});
