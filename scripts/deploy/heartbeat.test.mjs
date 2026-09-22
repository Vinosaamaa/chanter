import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { backupHeartbeatUrl, sendBackupHeartbeat } from './heartbeat.mjs';

const url = `https://o1.ingest.us.sentry.io/api/1/cron/chanter-backup-verification/${'a'.repeat(32)}/`;
const settings = { CHANTER_BACKUP_HEARTBEAT_URL: url };
const receipt = () => ({ status: 'ok', stale: false, checkedAt: new Date().toISOString(), release: 'b'.repeat(40) });

test('heartbeat URL requires the fixed private Sentry monitor without arbitrary query or redirect targets', () => {
  assert.equal(backupHeartbeatUrl({}), null);
  assert.equal(backupHeartbeatUrl(settings), url);
  for (const invalid of [url.replace('https:', 'http:'), url + '?private-canary', url + '#private-canary',
    url.replace('sentry.io', 'foreign.example'), url.replace('chanter-backup-verification', 'private-canary'), url + '\n']) {
    assert.throws(() => backupHeartbeatUrl({ CHANTER_BACKUP_HEARTBEAT_URL: invalid }),
      { message: 'Invalid private backup heartbeat configuration' });
  }
});

test('only a fresh verified backup can produce a content-free successful heartbeat', async () => {
  const calls = [];
  const send = async (target, options) => { calls.push({ target: String(target), options }); return new Response('', { status: 202 }); };
  assert.deepEqual(await sendBackupHeartbeat({}, 'production', receipt(), send), { status: 'disabled' });
  for (const invalid of [{ ...receipt(), status: 'failed' }, { ...receipt(), stale: true },
    { ...receipt(), checkedAt: '2020-01-01T00:00:00.000Z' }]) {
    await assert.rejects(sendBackupHeartbeat(settings, 'production', invalid, send), /verified backup/);
  }
  assert.equal(calls.length, 0);
  const result = await sendBackupHeartbeat(settings, 'production', { ...receipt(), private: 'private-canary' }, send);
  assert.equal(result.status, 'accepted');
  assert.equal(calls.length, 1);
  const target = new URL(calls[0].target);
  assert.equal(target.searchParams.get('status'), 'ok');
  assert.equal(target.searchParams.get('environment'), 'production');
  assert.match(target.searchParams.get('check_in_id'), /^[a-f0-9-]{36}$/);
  assert.deepEqual([...target.searchParams.keys()].sort(), ['check_in_id', 'environment', 'status']);
  assert.equal(calls[0].options.redirect, 'error');
  assert.equal(calls[0].options.credentials, 'omit');
  assert.equal(calls[0].options.body, undefined);
  assert.equal(JSON.stringify(calls).includes('private-canary'), false);
});

test('receiver rejection and outage preserve successful backup and never claim notification delivery', async () => {
  const original = receipt();
  for (const send of [async () => new Response('private-canary', { status: 500 }), async () => { throw new Error('private-canary'); }]) {
    const result = await sendBackupHeartbeat(settings, 'production', original, send);
    assert.deepEqual(result, { status: 'unconfirmed' });
    assert.equal(original.status, 'ok');
  }
});

test('actual HTTP transport sends no backup contents and aborts a silent receiver', async t => {
  const requests = [];
  let silent = false;
  const server = http.createServer((request, response) => {
    const chunks = [];
    request.on('data', chunk => chunks.push(chunk));
    request.on('end', () => {
      requests.push({ url: request.url, headers: request.headers, body: Buffer.concat(chunks).toString() });
      if (!silent) response.writeHead(202).end();
    });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const port = server.address().port;
  const send = (target, options) => {
    const requested = new URL(target);
    return fetch(`http://127.0.0.1:${port}${requested.pathname}${requested.search}`, options);
  };
  assert.deepEqual(await sendBackupHeartbeat(settings, 'production', { ...receipt(), secret: 'private-canary' }, send), { status: 'accepted' });
  assert.equal(requests[0].body, '');
  assert.equal(requests[0].headers.authorization, undefined);
  assert.equal(requests[0].headers.cookie, undefined);
  assert.equal(requests[0].headers.referer, undefined);
  assert.equal(JSON.stringify(requests).includes('private-canary'), false);
  silent = true;
  const started = performance.now();
  assert.deepEqual(await sendBackupHeartbeat(settings, 'production', receipt(), send), { status: 'unconfirmed' });
  assert.ok(performance.now() - started < 5000, 'silent monitor cannot hold up the scheduled command');
});
