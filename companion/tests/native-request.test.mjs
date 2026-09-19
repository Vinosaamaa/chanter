import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { mkdir, mkdtemp } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { NativeState } from '../src/native-state.mjs';
import { NativeRequestGate } from '../src/native-request.mjs';

async function fixture(approve = async () => true, transportFailure = false) {
  await mkdir('.cache/companion-state', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-state/gate-'));
  const state = await NativeState.open(path.join(parent, 'protected'));
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const origin = 'https://chanter.example';
  const calls = [];
  const gate = new NativeRequestGate({ state, origin, host: '127.0.0.1:43160', publicKey, approve,
    transport: async (request) => { calls.push(request); if (transportFailure) throw new Error('private-provider-error'); return { text: 'Synthetic answer.' }; } });
  const ticket = (payload) => {
    const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
    return body + '.' + sign(null, Buffer.from('chanter-native-v1.' + body), privateKey).toString('base64url');
  };
  const common = { version: 1, installationId: state.installationId, origin, userId: 'user-316', sessionId: 'session-316',
    issuedAt: Date.now(), expiresAt: Date.now() + 60_000 };
  const pairing = ticket({ ...common, kind: 'pair', requestId: 'pair-316' });
  const prompt = 'Approved synthetic evidence.';
  const payload = { ...common, kind: 'study', requestId: 'study-316', questionId: 'question-316', provider: 'codex',
    mode: 'quoted-evidence', model: 'gpt-6-astra', promptSha256: createHash('sha256').update(prompt).digest('hex'),
    evidenceSha256: [createHash('sha256').update('Synthetic source.').digest('hex')],
    maxInputBytes: 4096, maxOutputBytes: 1024, deadlineMs: 1000 };
  return { state, gate, calls, ticket, pairing, payload, prompt,
    headers: { host: '127.0.0.1:43160', origin, 'content-type': 'application/json' } };
}

test('signed native pairing and approved request consume durable capability before transport', async () => {
  const f = await fixture();
  try {
    const { handle } = await f.gate.pair(f.pairing);
    const request = { headers: { ...f.headers, 'x-chanter-pairing': handle }, ticket: f.ticket(f.payload), prompt: f.prompt };
    assert.deepEqual(await f.gate.execute(request), { text: 'Synthetic answer.' });
    assert.equal(f.calls.length, 1);
    await assert.rejects(f.gate.execute(request), { code: 'CAPABILITY_ALREADY_USED' });
    assert.equal(f.calls.length, 1);
  } finally { f.state.close(); }
});

test('wrong Host/Origin, simple requests and spoofed origin without pairing never reach transport', async () => {
  const f = await fixture();
  try {
    const { handle } = await f.gate.pair(f.pairing);
    for (const headers of [
      { ...f.headers, 'x-chanter-pairing': handle, host: 'attacker.example:43160' },
      { ...f.headers, 'x-chanter-pairing': handle, origin: 'https://attacker.example' },
      { ...f.headers, 'x-chanter-pairing': handle, 'content-type': 'text/plain' },
      { ...f.headers },
    ]) {
      await assert.rejects(f.gate.execute({ headers, ticket: f.ticket(f.payload), prompt: f.prompt }),
        { code: headers['x-chanter-pairing'] ? 'NATIVE_ORIGIN_REJECTED' : 'PAIRING_REQUIRED' });
    }
    assert.equal(f.calls.length, 0);
  } finally { f.state.close(); }
});

test('signed scope changes, unsupported answer modes, invalid signatures and tampered evidence fail closed', async () => {
  const f = await fixture();
  try {
    const { handle } = await f.gate.pair(f.pairing);
    const headers = { ...f.headers, 'x-chanter-pairing': handle };
    for (const payload of [
      { ...f.payload, installationId: 'other-installation' }, { ...f.payload, provider: 'api-key' },
      { ...f.payload, mode: 'grounded-explanation' }, { ...f.payload, maxOutputBytes: 100_000 },
      { ...f.payload, expiresAt: Date.now() - 1 }, { ...f.payload, arbitraryUrl: 'https://attacker.example' },
    ]) await assert.rejects(f.gate.execute({ headers, ticket: f.ticket(payload), prompt: f.prompt }), { code: 'INVALID_CAPABILITY' });
    const signed = f.ticket(f.payload);
    const [body, signature] = signed.split('.');
    const invalidSignature = (signature[0] === 'A' ? 'B' : 'A') + signature.slice(1);
    await assert.rejects(f.gate.execute({ headers, ticket: body + '.' + invalidSignature, prompt: f.prompt }), { code: 'INVALID_CAPABILITY' });
    await assert.rejects(f.gate.execute({ headers, ticket: signed, prompt: f.prompt + 'tampered' }), { code: 'EVIDENCE_MISMATCH' });
    assert.equal(f.calls.length, 0);
  } finally { f.state.close(); }
});

test('logout during native approval prevents the pending request from reaching transport', async () => {
  let revoke;
  const f = await fixture(async (approval) => { if (approval.kind === 'study') revoke(); return true; });
  revoke = () => f.state.revokePairing();
  try {
    const { handle } = await f.gate.pair(f.pairing);
    await assert.rejects(f.gate.execute({ headers: { ...f.headers, 'x-chanter-pairing': handle },
      ticket: f.ticket(f.payload), prompt: f.prompt }), { code: 'PAIRING_REQUIRED' });
    assert.equal(f.calls.length, 0);
  } finally { f.state.close(); }
});

test('unknown provider completion retains the durable attempt and emits only a safe error', async () => {
  const f = await fixture(undefined, true);
  try {
    const { handle } = await f.gate.pair(f.pairing);
    const request = { headers: { ...f.headers, 'x-chanter-pairing': handle }, ticket: f.ticket(f.payload), prompt: f.prompt };
    await assert.rejects(f.gate.execute(request), { code: 'NATIVE_REQUEST_FAILED', message: 'NATIVE_REQUEST_FAILED' });
    await assert.rejects(f.gate.execute(request), { code: 'CAPABILITY_ALREADY_USED' });
    assert.equal(f.calls.length, 1);
  } finally { f.state.close(); }
});

test('an unanswered native approval expires without holding the request gate forever', { timeout: 10_000 }, async () => {
  const f = await fixture(async (approval) => approval.kind === 'pair' ? true : new Promise(() => {}));
  try {
    const { handle } = await f.gate.pair(f.pairing);
    const payload = { ...f.payload, issuedAt: Date.now(), expiresAt: Date.now() + 25 };
    await assert.rejects(f.gate.execute({ headers: { ...f.headers, 'x-chanter-pairing': handle },
      ticket: f.ticket(payload), prompt: f.prompt }), { code: 'INVALID_CAPABILITY' });
    assert.equal(f.calls.length, 0);
  } finally { f.state.close(); }
});
