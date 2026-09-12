import { accountSummary, limitSummary, modelSummary } from './codex-account.mjs';

const MAX_FRAME_BYTES = 1024 * 1024;

export class CompanionError extends Error {
  constructor(code) { super(code); this.name = 'CompanionError'; this.code = code; }
}

/** Private stdio connection: provider messages never become a browser RPC proxy. */
export class CodexAppServer {
  #child; #pending = new Map(); #nextId = 0; #buffer = Buffer.alloc(0);
  #closed = false; #initialized = false; #timeoutMs;
  #loginId = null; #loginStarting = false;

  constructor(child, { timeoutMs = 10_000 } = {}) {
    if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 30_000) throw new CompanionError('INVALID_TIMEOUT');
    this.#child = child;
    this.#timeoutMs = timeoutMs;
    child.stdout.on('data', (chunk) => this.#receive(chunk));
    child.stderr.resume(); // Provider diagnostics may contain paths, content, or credentials.
    child.on('error', () => this.close('PROVIDER_UNAVAILABLE'));
    child.on('exit', () => this.close('PROVIDER_DISCONNECTED'));
    child.stdin.on('error', () => this.close('PROVIDER_DISCONNECTED'));
    child.stdout.on('error', () => this.close('PROVIDER_DISCONNECTED'));
    child.stdout.on('end', () => this.close('PROVIDER_DISCONNECTED'));
  }

  async initialize() {
    if (this.#initialized) throw new CompanionError('ALREADY_INITIALIZED');
    await this.#request('initialize', {
      clientInfo: { name: 'chanter_companion', title: 'Chanter Companion', version: '0.1.0' },
      capabilities: { experimentalApi: false },
    });
    this.#write({ method: 'initialized' });
    this.#initialized = true;
  }

  async account() {
    this.#requireInitialized();
    return accountSummary(await this.#request('account/read', { refreshToken: false }));
  }

  async discover() {
    const account = await this.account();
    if (account.state !== 'subscription') return { account, models: [], limits: null };
    const models = new Map(), seenCursors = new Set();
    let cursor = null;
    for (let page = 0; page < 5; page++) {
      const result = await this.#request('model/list', { cursor, limit: 100, includeHidden: false });
      if (!Array.isArray(result?.data) || result.data.length > 100) throw new CompanionError('PROVIDER_PROTOCOL_ERROR');
      for (const entry of result.data) {
        const model = modelSummary(entry);
        if (model) models.set(model.id, model);
      }
      cursor = result.nextCursor;
      if (cursor == null) {
        const limits = limitSummary(await this.#request('account/rateLimits/read', {}));
        return { account, models: [...models.values()], limits };
      }
      if (typeof cursor !== 'string' || cursor.length < 1 || cursor.length > 512 || seenCursors.has(cursor)) {
        throw new CompanionError('PROVIDER_PROTOCOL_ERROR');
      }
      seenCursors.add(cursor);
    }
    throw new CompanionError('PROVIDER_PROTOCOL_ERROR');
  }

  /** Explicit native user action. Device codes remain local and must never be logged. */
  async beginLogin() {
    this.#requireInitialized();
    if (this.#loginStarting || this.#loginId) throw new CompanionError('LOGIN_IN_PROGRESS');
    this.#loginStarting = true;
    try {
      const login = await this.#request('account/login/start', { type: 'chatgptDeviceCode' });
      if (login?.type !== 'chatgptDeviceCode'
          || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(login.loginId ?? '')
          || login.verificationUrl !== 'https://auth.openai.com/codex/device'
          || !/^[A-Z0-9-]{4,24}$/.test(login.userCode ?? '')) {
        this.close('PROVIDER_PROTOCOL_ERROR');
        throw new CompanionError('PROVIDER_PROTOCOL_ERROR');
      }
      this.#loginId = login.loginId;
      return { verificationUrl: login.verificationUrl, userCode: login.userCode };
    } finally { this.#loginStarting = false; }
  }

  async cancelLogin() {
    this.#requireInitialized();
    if (!this.#loginId) return;
    await this.#request('account/login/cancel', { loginId: this.#loginId });
    this.#loginId = null;
  }

  close(code = 'COMPANION_CLOSED') {
    if (this.#closed) return;
    this.#closed = true;
    this.#loginId = null;
    this.#buffer = Buffer.alloc(0);
    for (const pending of this.#pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(new CompanionError(code));
    }
    this.#pending.clear();
    this.#child.stdin.end();
    this.#child.kill();
  }

  #requireInitialized() {
    if (!this.#initialized) throw new CompanionError('NOT_INITIALIZED');
  }

  #request(method, params) {
    if (this.#closed) return Promise.reject(new CompanionError('COMPANION_CLOSED'));
    const id = ++this.#nextId;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => this.close('PROVIDER_TIMEOUT'), this.#timeoutMs);
      this.#pending.set(id, { resolve, reject, timer });
      this.#write({ id, method, params });
    });
  }

  #write(message) {
    if (this.#closed) throw new CompanionError('COMPANION_CLOSED');
    this.#child.stdin.write(JSON.stringify(message) + '\n');
  }

  #receive(chunk) {
    if (this.#closed) return;
    this.#buffer = Buffer.concat([this.#buffer, chunk]);
    let end;
    while ((end = this.#buffer.indexOf(10)) >= 0) {
      if (end > MAX_FRAME_BYTES) return this.close('PROVIDER_PROTOCOL_ERROR');
      const line = this.#buffer.subarray(0, end);
      this.#buffer = this.#buffer.subarray(end + 1);
      try {
        const message = JSON.parse(line.toString('utf8'));
        if (!message || typeof message !== 'object' || Array.isArray(message)) throw new Error();
        if (message.method != null) {
          if (message.method === 'account/login/completed' && message.params?.loginId === this.#loginId) this.#loginId = null;
          // Never approve provider tools, credential refresh requests, or arbitrary server RPC.
          if (message.id != null) this.#write({ id: message.id, error: { code: -32601, message: 'Unsupported request' } });
          continue;
        }
        const pending = this.#pending.get(message.id);
        if (!pending) continue;
        clearTimeout(pending.timer);
        this.#pending.delete(message.id);
        if (message.error) pending.reject(new CompanionError('PROVIDER_REQUEST_FAILED'));
        else if (Object.hasOwn(message, 'result')) pending.resolve(message.result);
        else { pending.reject(new CompanionError('PROVIDER_PROTOCOL_ERROR')); this.close('PROVIDER_PROTOCOL_ERROR'); }
      } catch { return this.close('PROVIDER_PROTOCOL_ERROR'); }
    }
    if (this.#buffer.length > MAX_FRAME_BYTES) this.close('PROVIDER_PROTOCOL_ERROR');
  }
}
