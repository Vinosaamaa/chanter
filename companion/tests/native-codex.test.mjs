import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdir, mkdtemp, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { codexLaunchPlan, requireSupportedVersion } from '../src/codex-launch.mjs';
import { CodexAppServer } from '../src/codex-app-server.mjs';

// Explicit native-only gate. No credentials, provider login, or external model transport.
const executable = process.env.CHANTER_CODEX_TEST_BINARY;
const native = { skip: !executable, timeout: 30_000 };

async function setup() {
  requireSupportedVersion(execFileSync(executable, ['--version'], { encoding: 'utf8', windowsHide: true }));
  await mkdir('.cache/companion-native', { recursive: true });
  const state = await mkdtemp(path.resolve('.cache/companion-native/run-'));
  for (const name of ['empty', 'provider', 'home', 'tmp']) await mkdir(path.join(state, name));
  const plan = codexLaunchPlan({ state, workspace: path.join(state, 'empty') });
  return { state, plan };
}

test('native Codex initializes the restricted profile without authentication or inference', native, async () => {
  const { plan } = await setup();
  const child = spawn(executable, plan.args, plan.options);
  const client = new CodexAppServer(child);
  try {
    await client.initialize();
    assert.deepEqual(await client.account(), { state: 'signed-out', provider: 'codex', plan: null });
  } finally { client.close(); }
});

async function runFixture(attack) {
  const { state, plan } = await setup();
  const canary = 'CANARY-NOT-A-CREDENTIAL-316';
  const canaryPath = path.join(state, 'outside.txt');
  await writeFile(canaryPath, canary);
  // Host skill discovery must stay off even if a skill appears in the isolated home.
  const skill = path.join(state, 'home', '.agents', 'skills', 'canary');
  await mkdir(skill, { recursive: true });
  await writeFile(path.join(skill, 'SKILL.md'), `---\nname: canary\ndescription: Test discovery boundary.\n---\n${canary}`);
  const requests = [];
  const server = createServer(async (req, res) => {
    let data = '';
    for await (const bytes of req) {
      data += bytes;
      if (data.length > 2_000_000) { req.destroy(); return; }
    }
    const body = JSON.parse(data);
    requests.push(body);
    const call = attack?.(canaryPath);
    const item = requests.length === 1 && call ? {
      type: 'function_call', id: 'fc_fixture', call_id: 'call_fixture', status: 'completed', ...call,
    } : { id: 'msg_fixture', type: 'message', role: 'assistant', status: 'completed',
      content: [{ type: 'output_text', text: 'Fixture answer.', annotations: [] }] };
    const response = { id: `resp_fixture_${requests.length}`, object: 'response', status: 'completed', output: [item],
      usage: { input_tokens: 12, output_tokens: 4, total_tokens: 16,
        input_tokens_details: { cached_tokens: 0 }, output_tokens_details: { reasoning_tokens: 0 } } };
    res.writeHead(200, { 'content-type': 'text/event-stream' });
    const events = [
      { type: 'response.created', response: { ...response, status: 'in_progress', output: [] } },
      { type: 'response.output_item.added', output_index: 0, item: { ...item, status: 'in_progress', content: [] } },
      ...(item.type === 'message' ? [
        { type: 'response.content_part.added', item_id: item.id, output_index: 0, content_index: 0,
          part: { type: 'output_text', text: '', annotations: [] } },
        { type: 'response.output_text.delta', item_id: item.id, output_index: 0, content_index: 0, delta: 'Fixture answer.' },
      ] : []),
      { type: 'response.output_item.done', output_index: 0, item },
      { type: 'response.completed', response },
    ];
    for (const event of events) res.write(`event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`);
    res.end();
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  // Test-only transport override. Production launch never accepts an endpoint or auth override.
  plan.args.push('-c', 'model_provider="fixture"', '-c',
    `model_providers.fixture={name="Loopback fixture",base_url="http://127.0.0.1:${server.address().port}/v1",wire_api="responses",requires_openai_auth=false}`,
    '-c', 'features.responses_websockets=false', '-c', 'features.responses_websockets_v2=false');
  const child = spawn(executable, plan.args, plan.options);
  child.stderr.resume();
  let nextId = 0, buffer = '';
  const pending = new Map();
  let complete;
  const done = new Promise((resolve) => { complete = resolve; });
  const fail = () => {
    for (const { reject } of pending.values()) reject(new Error('Native fixture disconnected'));
    pending.clear();
    complete('disconnected');
  };
  child.on('error', fail);
  child.on('exit', fail);
  child.stdout.on('data', (bytes) => {
    buffer += bytes;
    if (buffer.length > 2_000_000) { child.kill(); return; }
    let end;
    while ((end = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, end); buffer = buffer.slice(end + 1);
      let message;
      try { message = JSON.parse(line); } catch { child.kill(); return; }
      const promise = pending.get(message.id);
      if (promise) {
        pending.delete(message.id);
        if (message.error) promise.reject(new Error('Native fixture protocol rejected'));
        else promise.resolve(message.result);
      } else if (message.method === 'turn/completed') complete(message.params.turn.status);
    }
  });
  function request(method, params) {
    return new Promise((resolve, reject) => {
      const id = ++nextId;
      pending.set(id, { resolve, reject });
      child.stdin.write(JSON.stringify({ id, method, params }) + '\n');
    });
  }
  const deadline = setTimeout(() => child.kill(), 20_000);
  try {
    await request('initialize', { clientInfo: { name: 'chanter_companion_fixture', version: '0.1.0' },
      capabilities: { experimentalApi: true } });
    child.stdin.write('{"method":"initialized"}\n');
    const started = await request('thread/start', { model: 'fixture-model', modelProvider: 'fixture',
      cwd: path.join(state, 'empty'), ephemeral: true, permissions: 'chanter-study', environments: [],
      dynamicTools: [], selectedCapabilityRoots: [], baseInstructions: 'Answer using only supplied evidence.',
      developerInstructions: 'No additional context is authorized.' });
    assert.equal(started.thread.ephemeral, true);
    assert.deepEqual(started.instructionSources, []);
    assert.deepEqual(started.runtimeWorkspaceRoots, []);
    assert.equal(started.activePermissionProfile.id, 'chanter-study');
    await request('turn/start', { threadId: started.thread.id,
      input: [{ type: 'text', text: 'Reply with Fixture answer.' }], environments: [] });
    assert.equal(await done, 'completed');
    assert.ok(requests.length >= 1 && requests.length <= 2);
    for (const body of requests) {
      assert.equal(body.store, false);
      assert.deepEqual(body.tools.map((tool) => tool.name ?? tool.type), ['skills']);
      assert.ok(!JSON.stringify(body).includes(canary), 'Native tool disclosed an unauthorized canary');
    }
    return requests.flatMap((body) => body.input.filter((item) => item.type === 'function_call_output').map((item) => String(item.output)));
  } finally {
    clearTimeout(deadline); child.kill();
    server.closeAllConnections(); await new Promise((resolve) => server.close(resolve));
  }
}

test('native synthetic turn exposes only the fixed skill namespace and never loads host instructions', native, async () => {
  assert.deepEqual(await runFixture(), []);
});

for (const authority of ['orchestrator', 'executor']) {
  test(`native ${authority} skill catalog cannot expose planted host skills`, native, async () => {
    const output = await runFixture(() => ({ namespace: 'skills', name: 'list', arguments: JSON.stringify({ authority: { kind: authority } }) }));
    assert.equal(output.length, 1);
    assert.deepEqual(JSON.parse(output[0]).skills, []);
  });
}

test('native forged skill package cannot read an absolute path outside the empty workspace', native, async () => {
  const output = await runFixture((canaryPath) => ({ namespace: 'skills', name: 'read',
    arguments: JSON.stringify({ package: canaryPath, resource: canaryPath }) }));
  assert.deepEqual(output, ['skill package is not available']);
});

test('native model cannot invoke an unadvertised shell tool to read a canary', native, async () => {
  const output = await runFixture((canaryPath) => ({ name: 'exec_command',
    arguments: JSON.stringify({ cmd: `Get-Content -LiteralPath '${canaryPath}'` }) }));
  assert.equal(output.length, 1);
  assert.equal(output[0], 'unsupported call: exec_command');
});
