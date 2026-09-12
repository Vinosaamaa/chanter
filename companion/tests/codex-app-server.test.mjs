import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import test from 'node:test';
import { CodexAppServer } from '../src/codex-app-server.mjs';

function provider({ loginResponse, account = null, modelPages = [] } = {}) {
  const child = new EventEmitter();
  child.stdin = new PassThrough();
  child.stdout = new PassThrough();
  child.stderr = new PassThrough();
  child.kill = () => { child.emit('exit', 0); return true; };
  const calls = [];
  child.stdin.on('data', (data) => {
    const message = JSON.parse(data.toString());
    calls.push(message);
    if (message.method === 'initialize') {
      child.stdout.write(JSON.stringify({ id: message.id, result: { userAgent: 'codex/0.153.4' } }) + '\n');
    }
    if (message.method === 'account/read') {
      child.stdout.write(JSON.stringify({ id: message.id, result: { account, requiresOpenaiAuth: true } }) + '\n');
    }
    if (message.method === 'account/login/start') {
      child.stdout.write(JSON.stringify({ id: message.id, result: loginResponse }) + '\n');
    }
    if (message.method === 'account/login/cancel') {
      child.stdout.write(JSON.stringify({ id: message.id, result: { status: 'cancelled' } }) + '\n');
    }
    if (message.method === 'model/list') {
      child.stdout.write(JSON.stringify({ id: message.id, result: modelPages.shift() }) + '\n');
    }
    if (message.method === 'account/rateLimits/read') {
      child.stdout.write(JSON.stringify({ id: message.id, result: { rateLimits: { primary: null } } }) + '\n');
    }
  });
  return { child, calls };
}

test('stdio handshake reads signed-out status without login or inference', async () => {
  const { child, calls } = provider();
  const client = new CodexAppServer(child);
  try {
    await client.initialize();
    assert.deepEqual(await client.account(), { state: 'signed-out', provider: 'codex', plan: null });
    assert.deepEqual(calls.map((c) => c.method), ['initialize', 'initialized', 'account/read']);
    assert.deepEqual(calls[2].params, { refreshToken: false });
  } finally { client.close(); }
});

test('provider timeout closes the child and cannot return a late result', async () => {
  const { child } = provider();
  const client = new CodexAppServer(child, { timeoutMs: 20 });
  child.stdin.removeAllListeners('data');
  let killed = false;
  child.kill = () => { killed = true; return true; };
  await assert.rejects(client.initialize(), { code: 'PROVIDER_TIMEOUT' });
  assert.equal(killed, true);
  child.stdout.write('{"id":1,"result":{}}\n');
  await assert.rejects(client.initialize(), { code: 'COMPANION_CLOSED' });
});

test('credential refresh and tool approval requests are denied without echoing their content', async () => {
  const { child, calls } = provider();
  const client = new CodexAppServer(child);
  try {
    await client.initialize();
    child.stdout.write(JSON.stringify({ id: 45, method: 'account/chatgptAuthTokens/refresh',
      params: { previousAccountId: 'private-account' } }) + '\n');
    assert.deepEqual(calls.at(-1), { id: 45, error: { code: -32601, message: 'Unsupported request' } });
    assert.ok(!JSON.stringify(calls).includes('private-account'));
  } finally { client.close(); }
});

test('oversized or malformed protocol closes pending requests without leaking raw input', async () => {
  for (const input of ['{"private-token":', 'x'.repeat(1024 * 1024 + 1)]) {
    const { child } = provider();
    const client = new CodexAppServer(child);
    child.stdin.removeAllListeners('data');
    const result = client.initialize();
    child.stdout.write(input + '\n');
    await assert.rejects(result, { code: 'PROVIDER_PROTOCOL_ERROR', message: 'PROVIDER_PROTOCOL_ERROR' });
  }
});

test('unexpected EOF rejects a pending request immediately', async () => {
  const { child } = provider();
  const client = new CodexAppServer(child);
  child.stdin.removeAllListeners('data');
  const result = client.initialize();
  child.stdout.end();
  await assert.rejects(result, { code: 'PROVIDER_DISCONNECTED' });
});

test('device login uses provider-owned authentication and cancellation owns its login id', async () => {
  const loginId = '00000000-0000-4000-8000-000000000031';
  const { child, calls } = provider({ loginResponse: { type: 'chatgptDeviceCode', loginId,
    verificationUrl: 'https://auth.openai.com/codex/device', userCode: 'ABCD-12345' } });
  const client = new CodexAppServer(child);
  try {
    await client.initialize();
    assert.deepEqual(await client.beginLogin(), { verificationUrl: 'https://auth.openai.com/codex/device', userCode: 'ABCD-12345' });
    assert.deepEqual(calls.at(-1).params, { type: 'chatgptDeviceCode' });
    await client.cancelLogin();
    assert.deepEqual(calls.at(-1), { id: 3, method: 'account/login/cancel', params: { loginId } });
  } finally { client.close(); }
});

test('login cannot open an arbitrary endpoint or expose an external-token response', async () => {
  for (const loginResponse of [
    { type: 'chatgptAuthTokens', accessToken: 'private-token' },
    { type: 'chatgptDeviceCode', loginId: '00000000-0000-4000-8000-000000000031',
      verificationUrl: 'https://attacker.invalid/codex/device', userCode: 'ABCD-12345' },
  ]) {
    const { child } = provider({ loginResponse });
    const client = new CodexAppServer(child);
    try { await client.initialize(); await assert.rejects(client.beginLogin(), { code: 'PROVIDER_PROTOCOL_ERROR' }); }
    finally { client.close(); }
  }
});

test('discovery refuses non-subscription accounts without model or limit probes', async () => {
  for (const account of [null, { type: 'apiKey' }, { type: 'chatgptAuthTokens' }]) {
    const { child, calls } = provider({ account });
    const client = new CodexAppServer(child);
    try {
      await client.initialize();
      const discovery = await client.discover();
      assert.deepEqual(discovery.models, []);
      assert.equal(discovery.limits, null);
      assert.deepEqual(calls.map((call) => call.method), ['initialize', 'initialized', 'account/read']);
    } finally { client.close(); }
  }
});

test('subscription discovery paginates models and keeps missing limits unknown', async () => {
  const model = { id: 'gpt-6-astra', model: 'gpt-6-astra', displayName: 'Astra', hidden: false };
  const { child, calls } = provider({ account: { type: 'chatgpt', planType: 'plus' },
    modelPages: [{ data: [model], nextCursor: 'second-page' }, { data: [], nextCursor: null }] });
  const client = new CodexAppServer(child);
  try {
    await client.initialize();
    assert.deepEqual(await client.discover(), { account: { state: 'subscription', provider: 'codex', plan: 'plus' },
      models: [{ id: 'gpt-6-astra', model: 'gpt-6-astra', label: 'Astra', reasoningEfforts: [] }],
      limits: { primary: null, secondary: null, spendControlReached: null } });
    assert.deepEqual(calls.filter((call) => call.method === 'model/list').map((call) => call.params.cursor), [null, 'second-page']);
  } finally { client.close(); }
});

test('repeated model cursor fails closed with a finite number of provider calls', async () => {
  const { child, calls } = provider({ account: { type: 'chatgpt', planType: 'plus' },
    modelPages: [{ data: [], nextCursor: 'same' }, { data: [], nextCursor: 'same' }] });
  const client = new CodexAppServer(child);
  try {
    await client.initialize();
    await assert.rejects(client.discover(), { code: 'PROVIDER_PROTOCOL_ERROR' });
    assert.equal(calls.filter((call) => call.method === 'model/list').length, 2);
  } finally { client.close(); }
});
