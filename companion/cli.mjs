#!/usr/bin/env node
import { lstat, readFile } from 'node:fs/promises';
import path from 'node:path';
import { openNativeApplication } from './src/native-application.mjs';
import { CompanionError } from './src/codex-app-server.mjs';
import { NativeConsole } from './src/native-console.mjs';
import { installCompanion, readConfiguration } from './src/native-install.mjs';
import { verifyNativeBinary } from './src/native-provider.mjs';
import { NativeState } from './src/native-state.mjs';

async function main() {
  if (Number(process.versions.node.split('.')[0]) !== 24) throw new CompanionError('NODE_24_REQUIRED');
  const [command, ...args] = process.argv.slice(2);
  if (!command || command === 'help') {
    console.log('Unsigned Chanter developer companion. Windows + Node 24 + user-installed Codex 0.153.4.\n' +
      'Install: node companion/cli.mjs install --directory <absolute-directory> --origin <https-origin> --public-key <public-PEM-file> --codex <absolute-executable> [--port 43160]\n' +
      'Start: node companion/cli.mjs start --directory <absolute-directory>\n' +
      'Setup status: node companion/cli.mjs status --directory <absolute-directory>\n' +
      'The running terminal accepts status, pair <signed-ticket>, revoke, login, logout, restart, stop.\n' +
      'Backend issuance and hosted UI wiring are still required. This is not a signed installer.');
    return;
  }
  const options = {};
  for (let i = 0; i < args.length; i += 2) {
    if (!['--directory', '--origin', '--public-key', '--codex', '--port'].includes(args[i]) || !args[i + 1] || Object.hasOwn(options, args[i])) throw new CompanionError('INVALID_NATIVE_ARGUMENTS');
    options[args[i]] = args[i + 1];
  }
  const directory = options['--directory'];
  if (!directory || !path.isAbsolute(directory)) throw new CompanionError('INVALID_NATIVE_ARGUMENTS');
  if (command === 'install') {
    await verifyNativeBinary(options['--codex'] ?? '');
    const keyFile = options['--public-key'];
    if (!keyFile || (await lstat(keyFile)).size > 4096) throw new CompanionError('INVALID_NATIVE_CONFIGURATION');
    console.log(JSON.stringify(await installCompanion({ directory, origin: options['--origin'], publicKey: await readFile(keyFile, 'utf8'),
      codexPath: options['--codex'], port: options['--port'] ? Number(options['--port']) : 43160 })));
    console.log('Installed an unsigned developer build. Open start.ps1 in a visible PowerShell terminal.');
    return;
  }
  if (Object.keys(options).length !== 1) throw new CompanionError('INVALID_NATIVE_ARGUMENTS');
  if (command === 'status') {
    const config = await readConfiguration(directory);
    const state = await NativeState.open(directory);
    try {
      console.log(JSON.stringify({ installed: true, build: config.build, distribution: config.distribution, origin: config.origin,
        installationId: state.installationId, providerVersion: await verifyNativeBinary(config.codexPath),
        account: 'not-probed', listener: 'not-probed', note: 'Use status in the running native terminal to query its account and listener.' }));
    } finally { state.close(); }
    return;
  }
  if (command !== 'start') throw new CompanionError('INVALID_NATIVE_ARGUMENTS');
  let application, stopping = false;
  const ui = new NativeConsole({ onCommand: async (line) => {
    if (!application || stopping) return;
    if (line === 'stop') { await stop(); return; }
    if (line === 'restart') { await application.restart(); ui.say('Listener restarted. Previous pairing handles are revoked.'); return; }
    if (line === 'status') { ui.say(JSON.stringify(await application.status())); return; }
    if (line === 'revoke') { application.revoke(); ui.say('Pairing revoked.'); return; }
    if (line.startsWith('pair ')) { ui.say(JSON.stringify(await application.pair(line.slice(5)))); return; }
    if (line === 'logout') { await application.logout(); ui.say('Provider sign-out requested; pairing revoked.'); return; }
    if (line === 'login') {
      const account = await application.login(async (device, { signal }) => {
        ui.say(`Provider device sign-in: ${device.verificationUrl}\nDevice code: ${device.userCode}`);
        return ui.approve({ kind: 'login-completion', instruction: 'Complete provider sign-in, then approve to check the account; deny cancels it.' }, { signal });
      });
      ui.say(JSON.stringify(account)); return;
    }
    ui.say('Commands: status, pair <signed-ticket>, revoke, login, logout, restart, stop.');
  } });
  async function stop() {
    if (stopping) return;
    stopping = true;
    try { await application?.close(); }
    finally { ui.close(); process.off('SIGINT', interrupt); process.off('SIGTERM', interrupt); }
  }
  const interrupt = () => { void stop().catch(() => { process.exitCode = 1; }); };
  process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
  process.stdin.once('end', interrupt);
  try {
    application = await openNativeApplication(directory, (summary, options) => ui.approve(summary, options));
    if (stopping) { await application.close(); return; }
    ui.say(`Unsigned developer companion running on loopback port ${application.port}. Installation: ${application.installationId}`);
    ui.say('Type status to inspect the provider account, or stop to exit.');
  } catch (error) { await stop(); throw error; }
}

main().catch((error) => { console.error(error instanceof CompanionError ? error.code : 'NATIVE_COMMAND_FAILED'); process.exitCode = 1; });
