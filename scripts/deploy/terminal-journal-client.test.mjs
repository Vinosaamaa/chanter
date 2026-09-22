import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { lifecycleClient } from './terminal-journal-client.mjs';
import { START } from './deleted-scope.mjs';

test('container transport uses fixed routes, private stdin and bounded execution without host token access', async () => {
  const calls = [];
  const client = lifecycleClient({ source: 'auth', environment: 'staging', composeFile: path.resolve('.cache/fixture-compose.json'),
    execute: (exe, args, options) => { calls.push({ exe, args, options }); return args.includes('checkpoint-get') ? '{"checkpoint":null}' : '{}'; } });
  assert.equal(await client.checkpoint(), null);
  await client.page(0, null);
  await client.acknowledge({ private: 'request-canary' });
  assert.ok(calls.every(call => call.exe === 'docker' && call.args.includes('auth-service') && call.args.includes('Lifecycle')));
  assert.equal(JSON.stringify(calls.map(call => call.args)).includes('canary'), false);
  assert.equal(calls[2].options.input, '{"private":"request-canary"}');
  assert.ok(calls.every(call => call.options.timeout === 30000 && call.options.maxBuffer === 256 * 1024));
  await assert.rejects(client.page('0&address=bad', null));
  await assert.rejects(client.reapply({ large: 'x'.repeat(256 * 1024) }));
});

test('scope selectors and import bodies stay on bounded stdin with fixed owning operations', async () => {
  const calls = [], entry = { targetId: '22222222-2222-4222-8222-222222222222', revision: 1,
    eventId: '11111111-1111-4111-8111-111111111111', digest: 'a'.repeat(64) };
  const client = lifecycleClient({ source: 'community', environment: 'staging', composeFile: path.resolve('.cache/fixture.json'),
    execute: (exe, args, options) => { calls.push({ args, options }); return '{}'; } });
  await client.scope(entry, 'COURSE', START);
  await client.importScope({ entry, page: { private: 'scope-canary' } });
  assert.ok(calls[0].args.includes('scope-read')); assert.ok(calls[1].args.includes('scope-import'));
  assert.equal(JSON.stringify(calls.map(call => call.args)).includes(entry.targetId), false);
  assert.equal(JSON.stringify(calls.map(call => call.args)).includes('scope-canary'), false);
  assert.equal(calls[0].options.input, [entry.targetId, '1', entry.eventId, entry.digest, 'COURSE', START, ''].join('\n'));
  assert.ok(calls[1].options.input.startsWith(entry.targetId + '\n{'));
  await assert.rejects(client.scope(entry, 'http://private', START));
  await assert.rejects(client.importScope({ entry, page: { large: 'x'.repeat(32768) } }));
  assert.equal(calls.length, 2);
});

test('container failures and invalid output cannot become successful participant receipts', async () => {
  for (const execute of [() => { throw Error('private-response-canary'); }, () => 'private-invalid-response']) {
    const client = lifecycleClient({ source: 'search', environment: 'production', composeFile: path.resolve('.cache/fixture.json'), execute });
    await assert.rejects(client.receipt(), error => !error.message.includes('canary') && !error.message.includes('private-invalid'));
    await assert.rejects(client.checkpoint());
    await assert.rejects(client.invalidate({}));
  }
  for (const source of ['gateway', '--privileged', 'http://private.example']) {
    assert.throws(() => lifecycleClient({ source, environment: 'staging', composeFile: path.resolve('.cache/fixture.json') }));
  }
});
