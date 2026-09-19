import assert from 'node:assert/strict';
import { PassThrough } from 'node:stream';
import test from 'node:test';
import { NativeConsole } from '../src/native-console.mjs';

test('native terminal requires an explicit challenge response and escapes evidence control characters', async () => {
  const input = new PassThrough(); input.isTTY = true;
  const output = new PassThrough(); output.isTTY = true;
  let text = ''; output.on('data', (chunk) => { text += chunk; });
  const ui = new NativeConsole({ input, output, onCommand: () => {} });
  try {
    const result = ui.approve({ kind: 'study', provider: 'codex', model: 'fixture', questionId: 'q', prompt: '\u001b[2Jsecret\u202e' });
    assert.ok(!text.includes('\u001b[2J'));
    assert.ok(!text.includes('\u202e'));
    const challenge = /approve ([a-f0-9]{12})/.exec(text)?.[1];
    assert.ok(challenge);
    input.write(`approve ${challenge}\n`);
    assert.equal(await result, true);
  } finally { ui.close(); }
});

test('redirected stdin cannot authorize native requests', () => {
  assert.throws(() => new NativeConsole({ input: new PassThrough(), output: new PassThrough(), onCommand: () => {} }),
    { code: 'INTERACTIVE_TERMINAL_REQUIRED' });
});

test('expired approval clears the native prompt and stale answers cannot authorize a later request', async () => {
  const input = new PassThrough(); input.isTTY = true;
  const output = new PassThrough(); output.isTTY = true;
  let text = ''; output.on('data', (chunk) => { text += chunk; });
  const ui = new NativeConsole({ input, output, onCommand: () => {} });
  try {
    const controller = new AbortController();
    const expired = ui.approve({ kind: 'pair' }, { signal: controller.signal });
    const first = /approve ([a-f0-9]{12})/.exec(text)[1];
    controller.abort(); assert.equal(await expired, false);
    const next = ui.approve({ kind: 'pair' });
    input.write(`approve ${first}\n`);
    input.write('deny\n');
    assert.equal(await next, false);
  } finally { ui.close(); }
});
