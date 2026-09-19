import { execFile, spawn } from 'node:child_process';
import { lstat, mkdir, realpath } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { CodexAppServer, CompanionError } from './codex-app-server.mjs';
import { codexLaunchPlan, requireSupportedVersion } from './codex-launch.mjs';
import { createRun, finishRun, markProvider, recoverRuns, removeProviderTemporary } from './native-retention.mjs';
import { ownCreatedEntry } from './native-state.mjs';

export async function verifyNativeBinary(executable, signal) {
  if (process.platform !== 'win32') throw new CompanionError('UNSUPPORTED_NATIVE_PLATFORM');
  if (!path.isAbsolute(executable) || path.extname(executable).toLowerCase() !== '.exe') throw new CompanionError('INVALID_NATIVE_EXECUTABLE');
  try {
    if (!(await lstat(executable)).isFile()) throw new Error();
    const { stdout } = await promisify(execFile)(executable, ['--version'], {
      windowsHide: true, timeout: 5000, maxBuffer: 1024, signal,
      env: { SystemRoot: process.env.SystemRoot, WINDIR: process.env.WINDIR },
    });
    return requireSupportedVersion(stdout);
  } catch (error) { throw error instanceof CompanionError ? error : new CompanionError('PROVIDER_UNAVAILABLE'); }
}

/** One owned process at a time. Authentication stays in the provider's persistent private home. */
export class NativeProvider {
  #root; #installationId; #executable; #active = null; #blocked = false;
  constructor({ root, installationId, executable }) {
    this.#root = root; this.#installationId = installationId; this.#executable = executable;
  }

  async initialize() {
    await verifyNativeBinary(this.#executable);
    await recoverRuns(this.#root, this.#installationId);
    try { await mkdir(path.join(this.#root, 'provider'), { mode: 0o700 }); await ownCreatedEntry(this.#root, 'provider'); }
    catch (error) { if (error.code !== 'EEXIST') throw error; }
    const providerHome = path.join(this.#root, 'provider');
    const stat = await lstat(providerHome);
    if (!stat.isDirectory() || stat.isSymbolicLink() || (await realpath(providerHome)).toLowerCase() !== path.resolve(providerHome).toLowerCase()) {
      throw new CompanionError('NATIVE_STATE_UNPROTECTED');
    }
  }

  status({ signal } = {}) { return this.#using((client) => client.discover(), 30_000, signal); }

  login(showDevice) {
    return this.#using(async (client, signal) => {
      const device = await client.beginLogin();
      if (await showDevice(device, { signal }) !== true) await client.cancelLogin();
      return client.account();
    }, 300_000);
  }

  logout() { return this.#using((client) => client.logout()); }

  study(request, { signal, onDelta } = {}) {
    return this.#using(async (client, cancellation) => {
      const discovery = await client.discover();
      if (discovery.account.state !== 'subscription') throw new CompanionError('SUBSCRIPTION_REQUIRED');
      if (!discovery.models.some((model) => model.model === request.model)) throw new CompanionError('SUBSCRIPTION_MODEL_UNAVAILABLE');
      const { primary, secondary, spendControlReached } = discovery.limits ?? {};
      if (!primary || !secondary) throw new CompanionError('SUBSCRIPTION_LIMITS_UNKNOWN');
      if (spendControlReached === true || primary.usedPercent >= 100 || secondary.usedPercent >= 100) throw new CompanionError('SUBSCRIPTION_LIMIT_REACHED');
      return client.studyTurn(request, { signal: cancellation, onDelta });
    }, request.deadlineMs, signal);
  }

  async stop() {
    const active = this.#active;
    if (!active) return;
    active.controller.abort(); active.client?.close('STUDY_CANCELLED');
    await active.finished;
  }

  async #using(operation, timeoutMs = 30_000, signal) {
    if (this.#blocked) throw new CompanionError('NATIVE_RETENTION_BLOCKED');
    if (this.#active) throw new CompanionError('NATIVE_PROVIDER_BUSY');
    const controller = new AbortController();
    let complete;
    const active = { controller, client: null, finished: new Promise((resolve) => { complete = resolve; }) };
    this.#active = active;
    const abort = () => { controller.abort(); active.client?.close('STUDY_CANCELLED'); };
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort();
    const timer = setTimeout(abort, timeoutMs);
    let run;
    try {
      await verifyNativeBinary(this.#executable, controller.signal);
      if (controller.signal.aborted) throw new CompanionError('STUDY_CANCELLED');
      run = await createRun(this.#root, this.#installationId);
      if (controller.signal.aborted) throw new CompanionError('STUDY_CANCELLED');
      const plan = codexLaunchPlan({ state: run.directory, workspace: path.join(run.directory, 'empty'), providerHome: path.join(this.#root, 'provider') });
      markProvider(run, null);
      const child = spawn(this.#executable, plan.args, plan.options);
      active.client = new CodexAppServer(child);
      markProvider(run, child.pid ?? 0);
      await active.client.initialize();
      return await operation(active.client, controller.signal);
    } catch (error) {
      if (error instanceof CompanionError && error.code === 'NATIVE_RETENTION_BLOCKED') this.#blocked = true;
      throw error instanceof CompanionError ? error : new CompanionError('PROVIDER_UNAVAILABLE');
    } finally {
      clearTimeout(timer); signal?.removeEventListener('abort', abort);
      try {
        await active.client?.stop();
        if (run) { await finishRun(this.#root, run, this.#installationId); await removeProviderTemporary(this.#root); }
      } catch { this.#blocked = true; throw new CompanionError('NATIVE_RETENTION_BLOCKED'); }
      finally { complete(); this.#active = null; }
    }
  }
}
