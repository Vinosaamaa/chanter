import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import test from 'node:test';
import { CodexAppServer } from '../src/codex-app-server.mjs';

function provider({ complete = true, delta = 'Evidence answer.', finalText = delta, profile = 'chanter-study' } = {}) {
  const child = new EventEmitter();
  child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
  let killed = false;
  child.kill = () => { killed = true; child.emit('exit', 0); return true; };
  const calls = [];
  const send = (value) => child.stdout.write(JSON.stringify(value) + '\n');
  child.stdin.on('data', (data) => {
    const call = JSON.parse(data.toString()); calls.push(call);
    if (call.method === 'initialize') send({ id: call.id, result: {} });
    if (call.method === 'thread/start') send({ id: call.id, result: {
      thread: { id: 'thread-316', ephemeral: true }, instructionSources: [], runtimeWorkspaceRoots: [],
      activePermissionProfile: { id: profile }, approvalPolicy: 'never', model: call.params.model,
    } });
    if (call.method === 'turn/start') {
      send({ id: call.id, result: { turn: { id: 'turn-316', status: 'inProgress', items: [] } } });
      queueMicrotask(() => {
        send({ method: 'item/agentMessage/delta', params: { threadId: 'thread-316', turnId: 'turn-316', itemId: 'answer-316', delta } });
        if (complete) {
          send({ method: 'item/completed', params: { threadId: 'thread-316', turnId: 'turn-316',
            item: { id: 'answer-316', type: 'agentMessage', text: finalText, phase: 'final_answer' } } });
          send({ method: 'thread/tokenUsage/updated', params: { threadId: 'thread-316', turnId: 'turn-316',
            tokenUsage: { last: { inputTokens: 0, outputTokens: 4 } } } });
          send({ method: 'turn/completed', params: { threadId: 'thread-316',
            turn: { id: 'turn-316', status: 'completed', items: [] } } });
        }
      });
    }
  });
  return { child, calls, send, get killed() { return killed; } };
}

const request = { model: 'gpt-6-astra', prompt: 'Use the supplied approved evidence.', maxInputBytes: 4096,
  maxOutputBytes: 1024, deadlineMs: 1000 };

test('private study turn streams bounded text with measured-zero usage and strict ephemeral context', async () => {
  const fixture = provider(); const client = new CodexAppServer(fixture.child);
  const deltas = [];
  try {
    await client.initialize();
    assert.deepEqual(await client.studyTurn(request, { onDelta: (delta) => deltas.push(delta) }), {
      text: 'Evidence answer.', usage: { inputTokens: 0, outputTokens: 4 }, provenance: 'native-client-report',
    });
    assert.deepEqual(deltas, ['Evidence answer.']);
    const start = fixture.calls.find((call) => call.method === 'thread/start');
    assert.equal(start.params.ephemeral, true);
    assert.deepEqual(start.params.environments, []);
    assert.deepEqual(start.params.dynamicTools, []);
    assert.deepEqual(start.params.selectedCapabilityRoots, []);
    assert.equal(start.params.permissions, 'chanter-study');
    assert.equal(fixture.calls.find((call) => call.method === 'turn/start').params.serviceTierForTurn, 'default');
  } finally { client.close(); }
});

test('completion cannot replace the streamed answer or append output after completion', async () => {
  const fixture = provider({ finalText: 'Changed answer.' }); const client = new CodexAppServer(fixture.child);
  await client.initialize();
  await assert.rejects(client.studyTurn(request), { code: 'PROVIDER_PROTOCOL_ERROR' });
  assert.equal(fixture.killed, true);
});

test('study input is bounded in UTF-8 bytes before any provider thread is created', async () => {
  const fixture = provider(); const client = new CodexAppServer(fixture.child);
  try {
    await client.initialize();
    await assert.rejects(client.studyTurn({ ...request, prompt: '🦉', maxInputBytes: 3 }), { code: 'INVALID_STUDY_REQUEST' });
    assert.equal(fixture.calls.some((call) => call.method === 'thread/start'), false);
  } finally { client.close(); }
});

test('changed native isolation rejects before approved evidence is sent', async () => {
  const fixture = provider({ profile: ':workspace' }); const client = new CodexAppServer(fixture.child);
  await client.initialize();
  await assert.rejects(client.studyTurn(request), { code: 'PROVIDER_ISOLATION_FAILED' });
  assert.equal(fixture.calls.some((call) => call.method === 'turn/start'), false);
  assert.equal(fixture.killed, true);
});

test('output overflow terminates the provider before the excess delta reaches a consumer', async () => {
  const fixture = provider({ delta: '🦉' }); const client = new CodexAppServer(fixture.child);
  const deltas = [];
  await client.initialize();
  await assert.rejects(client.studyTurn({ ...request, maxOutputBytes: 3 }, { onDelta: (value) => deltas.push(value) }),
    { code: 'STUDY_OUTPUT_LIMIT' });
  assert.deepEqual(deltas, []);
  assert.equal(fixture.killed, true);
});

test('abort and deadline terminate attempted turns without a reusable connection', async () => {
  for (const cancel of [true, false]) {
    const fixture = provider({ complete: false }); const client = new CodexAppServer(fixture.child);
    const controller = new AbortController();
    await client.initialize();
    const result = client.studyTurn({ ...request, deadlineMs: 25 }, {
      signal: controller.signal, onDelta: () => { if (cancel) controller.abort(); },
    });
    await assert.rejects(result, { code: cancel ? 'STUDY_CANCELLED' : 'STUDY_TIMEOUT' });
    assert.equal(fixture.killed, true);
    await assert.rejects(client.studyTurn(request), { code: 'STUDY_ALREADY_ATTEMPTED' });
  }
});

test('wrong-turn events and tool approval requests terminate without exposing their payloads', async () => {
  for (const message of [
    { method: 'item/agentMessage/delta', params: { threadId: 'thread-316', turnId: 'foreign', delta: 'private-content' } },
    { id: 45, method: 'item/commandExecution/requestApproval', params: { command: 'private-command' } },
  ]) {
    const fixture = provider({ complete: false }); const client = new CodexAppServer(fixture.child);
    await client.initialize();
    const result = client.studyTurn(request, { onDelta: () => fixture.send(message) });
    await assert.rejects(result, { code: message.id ? 'PROVIDER_TOOL_DENIED' : 'PROVIDER_PROTOCOL_ERROR' });
    assert.equal(fixture.killed, true);
    assert.ok(!JSON.stringify(fixture.calls).includes('private-'));
  }
});
