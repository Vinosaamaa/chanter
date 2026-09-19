import { createPublicKey } from 'node:crypto';
import { CompanionError } from './codex-app-server.mjs';
import { readConfiguration } from './native-install.mjs';
import { NativeProvider } from './native-provider.mjs';
import { startNativeServer } from './native-server.mjs';
import { NativeState } from './native-state.mjs';

/** The optional factory is a native composition seam for tests, never a CLI, environment, or HTTP option. */
export async function openNativeApplication(directory, approve, { providerFactory = (options) => new NativeProvider(options) } = {}) {
  const config = await readConfiguration(directory);
  const state = await NativeState.open(directory);
  try {
    const provider = providerFactory({ root: directory, installationId: state.installationId, executable: config.codexPath });
    const application = new NativeApplication(state, config, provider, approve);
    await application.start();
    return application;
  } catch (error) { state.close(); throw error; }
}

class NativeApplication {
  #state; #config; #provider; #approve; #server = null; #closed = false; #transition = Promise.resolve();
  constructor(state, config, provider, approve) { this.#state = state; this.#config = config; this.#provider = provider; this.#approve = approve; }
  get installationId() { return this.#state.installationId; }
  get port() { return this.#server?.port ?? null; }

  start() { return this.#serialize(() => this.#start()); }
  async #start() {
    if (this.#closed || this.#server) throw new CompanionError('NATIVE_LIFECYCLE_CONFLICT');
    await this.#provider.initialize();
    this.#state.revokePairing();
    this.#server = await startNativeServer({ state: this.#state, origin: this.#config.origin, port: this.#config.port,
      publicKey: createPublicKey(this.#config.publicKey), approve: this.#approve,
      transport: (request, options) => this.#provider.study(request, options) });
  }

  async status() {
    if (this.#closed) throw new CompanionError('NATIVE_NOT_RUNNING');
    return { build: this.#config.build, distribution: this.#config.distribution, state: this.#server ? 'running' : 'stopped',
      origin: this.#config.origin, installationId: this.installationId, port: this.port,
      provider: this.#server ? await this.#provider.status() : null };
  }
  pair(ticket) { if (!this.#server) throw new CompanionError('NATIVE_NOT_RUNNING'); return this.#server.pair(ticket); }
  revoke() { this.#state.revokePairing(); }
  login(showDevice) { this.revoke(); return this.#provider.login(showDevice); }
  async logout() { this.revoke(); await this.#provider.stop(); await this.#provider.logout(); }

  stop() { return this.#serialize(() => this.#stop()); }
  async #stop() {
    if (this.#closed) return;
    const server = this.#server; this.#server = null;
    try { await server?.close(); await this.#provider.stop(); }
    finally { this.#state.revokePairing(); }
  }
  restart() { return this.#serialize(async () => { await this.#stop(); await this.#start(); }); }
  close() {
    return this.#serialize(async () => {
      if (this.#closed) return;
      try { await this.#stop(); }
      finally { this.#closed = true; this.#state.close(); }
    });
  }
  #serialize(operation) {
    const next = this.#transition.then(operation);
    this.#transition = next.catch(() => {});
    return next;
  }
}
