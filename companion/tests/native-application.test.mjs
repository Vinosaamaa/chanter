import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { generateKeyPairSync } from 'node:crypto';
import { lstat, mkdir, mkdtemp, readFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';
import { installCompanion, readConfiguration } from '../src/native-install.mjs';
import { openNativeApplication } from '../src/native-application.mjs';

async function installation() {
  await mkdir('.cache/companion-operator', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-operator/install-'));
  const directory = path.join(parent, 'protected');
  const { publicKey } = generateKeyPairSync('ed25519');
  const portServer = createServer();
  await new Promise((resolve) => portServer.listen(0, '127.0.0.1', resolve));
  const port = portServer.address().port;
  await new Promise((resolve) => portServer.close(resolve));
  const options = { directory, origin: 'https://chanter.example', publicKey: publicKey.export({ type: 'spki', format: 'pem' }), codexPath: process.execPath, port };
  await installCompanion(options);
  return options;
}

function providerFactory() {
  return { initialize: async () => { await new Promise((resolve) => setTimeout(resolve, 5)); },
    status: async () => ({ account: { state: 'signed-out' }, models: [], limits: null }),
    stop: async () => {}, study: async () => { throw new Error('No inference is authorized by this fixture'); } };
}

test('operator installation, start, stop and restart preserve identity and report unsigned/signed-out state', async () => {
  const options = await installation();
  const app = await openNativeApplication(options.directory, async () => false, { providerFactory });
  const installationId = app.installationId;
  try {
    assert.equal((await app.status()).distribution, 'unsigned-developer');
    assert.equal((await app.status()).provider.account.state, 'signed-out');
    await app.stop(); assert.equal(app.port, null);
    await app.start(); assert.equal(app.port, options.port);
    await app.restart(); assert.equal(app.installationId, installationId);
    assert.equal((await readConfiguration(options.directory)).origin, options.origin);
    assert.ok((await readFile(path.join(options.directory, 'start.ps1'), 'utf8')).includes('Unsigned Chanter developer'));
    await assert.rejects(installCompanion(options), { code: 'NATIVE_ALREADY_INSTALLED' });
  } finally { await app.close(); }
  await assert.rejects(fetch(`http://127.0.0.1:${options.port}/study`));
});

test('setup status does not install an unknown directory and start rejects redirected approval input', async () => {
  await mkdir('.cache/companion-operator', { recursive: true });
  const parent = await mkdtemp(path.resolve('.cache/companion-operator/status-'));
  const directory = path.join(parent, 'not-installed');
  const run = promisify(execFile);
  await assert.rejects(run(process.execPath, ['companion/cli.mjs', 'status', '--directory', directory], { windowsHide: true }),
    (error) => error.stderr.trim() === 'NATIVE_NOT_CONFIGURED');
  await assert.rejects(lstat(directory), { code: 'ENOENT' });
  await assert.rejects(run(process.execPath, ['companion/cli.mjs', 'start', '--directory', directory], { windowsHide: true }),
    (error) => error.stderr.trim() === 'INTERACTIVE_TERMINAL_REQUIRED');
});

test('stop during restart is serialized and cannot leave an unowned listener running', async () => {
  const options = await installation();
  const app = await openNativeApplication(options.directory, async () => false, { providerFactory });
  const outcomes = await Promise.allSettled([app.restart(), app.close()]);
  assert.ok(outcomes.every((outcome) => outcome.status === 'fulfilled'));
  assert.equal(app.port, null);
  await assert.rejects(fetch(`http://127.0.0.1:${options.port}/study`));
});
