import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { NativeState } from '../src/native-state.mjs';
import { NativeProvider } from '../src/native-provider.mjs';

test('native provider status survives restart while per-operation databases are removed and auth home is preserved',
  { skip: !process.env.CHANTER_CODEX_TEST_BINARY, timeout: 30_000 }, async () => {
    await mkdir('.cache/companion-operator', { recursive: true });
    const parent = await mkdtemp(path.resolve('.cache/companion-operator/provider-'));
    const root = path.join(parent, 'protected');
    const state = await NativeState.open(root);
    const options = { root, installationId: state.installationId, executable: process.env.CHANTER_CODEX_TEST_BINARY };
    try {
      const provider = new NativeProvider(options); await provider.initialize();
      await writeFile(path.join(root, 'provider', 'synthetic-preserve'), 'not a credential');
      assert.equal((await provider.status()).account.state, 'signed-out');
      assert.deepEqual(await readdir(path.join(root, 'runs')), []);
      assert.ok((await readdir(path.join(root, 'provider'))).includes('synthetic-preserve'));
      assert.ok(!(await readdir(path.join(root, 'provider'))).some((name) => name.includes('.sqlite')));
      await provider.stop();
      const restarted = new NativeProvider(options); await restarted.initialize();
      assert.equal((await restarted.status()).account.state, 'signed-out');
      await assert.rejects(restarted.study({ model: 'unavailable', prompt: 'Synthetic.', deadlineMs: 5000 }), { code: 'SUBSCRIPTION_REQUIRED' });
      assert.deepEqual(await readdir(path.join(root, 'runs')), []);
      await restarted.stop();
    } finally { state.close(); }
  });
