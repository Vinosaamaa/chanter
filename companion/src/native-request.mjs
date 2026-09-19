import { createHash, verify } from 'node:crypto';
import { CompanionError } from './codex-app-server.mjs';

const commonKeys = ['version', 'kind', 'installationId', 'origin', 'userId', 'sessionId', 'requestId', 'issuedAt', 'expiresAt'];
const studyKeys = ['questionId', 'provider', 'mode', 'model', 'promptSha256', 'evidenceSha256', 'maxInputBytes', 'maxOutputBytes', 'deadlineMs'];
const identifier = (value) => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,80}$/.test(value);
const digest = (value) => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
const bounded = (value, max) => Number.isSafeInteger(value) && value > 0 && value <= max;

/** Internal native boundary. Deployment key, approval UI, state and transport never come from a website. */
export class NativeRequestGate {
  #state; #origin; #host; #key; #approve; #transport; #discover; #busy = false;
  constructor({ state, origin, host, publicKey, approve, transport, discover }) {
    let validOrigin = false;
    try { const parsed = new URL(origin); validOrigin = parsed.protocol === 'https:' && parsed.origin === origin; } catch { /* Reject. */ }
    const port = /^127\.0\.0\.1:([1-9][0-9]{0,4})$/.exec(host ?? '');
    if (!validOrigin || !port || Number(port[1]) > 65535 || publicKey?.asymmetricKeyType !== 'ed25519'
        || publicKey.type !== 'public' || typeof approve !== 'function' || typeof transport !== 'function') {
      throw new CompanionError('INVALID_NATIVE_CONFIGURATION');
    }
    this.#state = state; this.#origin = origin; this.#host = host; this.#key = publicKey;
    this.#approve = approve; this.#transport = transport; this.#discover = discover;
  }

  /** Invoke from native user action after displaying the backend-signed pairing identity. */
  async pair(ticket, { signal } = {}) {
    return this.#exclusive(async () => {
      const payload = this.#read(ticket, 'pair');
      if (await this.#approval(payload, { kind: 'pair', origin: payload.origin, userId: payload.userId }, signal) !== true) {
        throw new CompanionError('NATIVE_APPROVAL_REQUIRED');
      }
      this.#requireCurrent(payload);
      this.#state.consume('pair_' + payload.requestId, payload.expiresAt);
      return this.#state.pair({ ...payload, expiresAt: Date.now() + 300_000 });
    });
  }

  /** User-triggered, signed discovery. No credentials, generic RPC, or provider input. */
  async status(request, { signal } = {}) {
    return this.#exclusive(async () => {
      const { headers, ticket } = request ?? {};
      if (headers?.host !== this.#host || headers?.origin !== this.#origin || headers?.['content-type'] !== 'application/json') {
        throw new CompanionError('NATIVE_ORIGIN_REJECTED');
      }
      const payload = this.#read(ticket, 'status');
      this.#state.requirePairing(headers['x-chanter-pairing'], payload);
      if (signal?.aborted) throw new CompanionError('STUDY_CANCELLED');
      if (typeof this.#discover !== 'function') throw new CompanionError('NATIVE_STATUS_UNAVAILABLE');
      this.#state.consume('status_' + payload.requestId, payload.expiresAt);
      const result = await this.#discover({ signal });
      this.#requireCurrent(payload);
      this.#state.requirePairing(headers['x-chanter-pairing'], payload);
      return result;
    });
  }

  async execute(request, { signal, onDelta } = {}) {
    return this.#exclusive(async () => {
      const { headers, ticket, prompt } = request ?? {};
      if (headers?.host !== this.#host || headers?.origin !== this.#origin || headers?.['content-type'] !== 'application/json') {
        throw new CompanionError('NATIVE_ORIGIN_REJECTED');
      }
      const handle = headers['x-chanter-pairing'];
      const payload = this.#read(ticket, 'study');
      this.#state.requirePairing(handle, payload);
      if (typeof prompt !== 'string' || prompt.length === 0 || Buffer.byteLength(prompt) > payload.maxInputBytes
          || createHash('sha256').update(prompt).digest('hex') !== payload.promptSha256) throw new CompanionError('EVIDENCE_MISMATCH');
      if (await this.#approval(payload, { kind: 'study', origin: payload.origin, userId: payload.userId,
        questionId: payload.questionId, provider: payload.provider, model: payload.model, mode: payload.mode, prompt }, signal) !== true) {
        throw new CompanionError('NATIVE_APPROVAL_REQUIRED');
      }
      // Approval may be delayed while the user logs out or the backend ticket expires.
      this.#requireCurrent(payload);
      if (signal?.aborted) throw new CompanionError('STUDY_CANCELLED');
      this.#state.requirePairing(handle, payload);
      this.#state.consume('study_' + payload.requestId, payload.expiresAt);
      return this.#transport(Object.freeze({ model: payload.model, prompt, maxInputBytes: payload.maxInputBytes,
        maxOutputBytes: payload.maxOutputBytes, deadlineMs: Math.min(payload.deadlineMs, payload.expiresAt - Date.now()) }), { signal, onDelta });
    });
  }

  #read(ticket, kind) {
    try {
      if (typeof ticket !== 'string' || ticket.length > 12_000) throw new Error();
      const match = /^([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]{86})$/.exec(ticket);
      if (!match) throw new Error();
      const body = Buffer.from(match[1], 'base64url');
      const signature = Buffer.from(match[2], 'base64url');
      if (body.toString('base64url') !== match[1] || signature.toString('base64url') !== match[2]
          || !verify(null, Buffer.from('chanter-native-v1.' + match[1]), this.#key, signature)) throw new Error();
      const value = JSON.parse(body.toString('utf8'));
      const keys = kind === 'study' ? [...commonKeys, ...studyKeys] : commonKeys;
      if (!value || Array.isArray(value) || Object.keys(value).length !== keys.length
          || !keys.every((key) => Object.hasOwn(value, key)) || value.version !== 1 || value.kind !== kind
          || value.installationId !== this.#state.installationId || value.origin !== this.#origin
          || !['userId', 'sessionId', 'requestId'].every((key) => identifier(value[key]))) throw new Error();
      this.#requireCurrent(value);
      if (kind === 'study' && (!identifier(value.questionId) || value.provider !== 'codex' || value.mode !== 'quoted-evidence'
          || typeof value.model !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}$/.test(value.model)
          || !digest(value.promptSha256) || !Array.isArray(value.evidenceSha256) || value.evidenceSha256.length < 1
          || value.evidenceSha256.length > 32 || !value.evidenceSha256.every(digest)
          || !bounded(value.maxInputBytes, 128 * 1024) || !bounded(value.maxOutputBytes, 64 * 1024)
          || !bounded(value.deadlineMs, 60_000))) throw new Error();
      return value;
    } catch { throw new CompanionError('INVALID_CAPABILITY'); }
  }

  #requireCurrent(value) {
    const now = Date.now();
    if (!Number.isSafeInteger(value.issuedAt) || !Number.isSafeInteger(value.expiresAt)
        || value.issuedAt > now || value.expiresAt <= now || value.expiresAt <= value.issuedAt
        || value.expiresAt - value.issuedAt > 120_000) throw new CompanionError('INVALID_CAPABILITY');
  }

  async #approval(payload, summary, signal) {
    if (signal?.aborted) throw new CompanionError('STUDY_CANCELLED');
    const approval = new AbortController();
    let timer, abort;
    const stopped = new Promise((_, reject) => {
      timer = setTimeout(() => reject(new CompanionError('INVALID_CAPABILITY')), Math.max(0, payload.expiresAt - Date.now()));
      abort = () => { approval.abort(); reject(new CompanionError('STUDY_CANCELLED')); };
      signal?.addEventListener('abort', abort, { once: true });
    });
    try { return await Promise.race([stopped, Promise.resolve().then(() => this.#approve(Object.freeze(summary), { signal: approval.signal }))]); }
    finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); approval.abort(); }
  }

  async #exclusive(action) {
    if (this.#busy) throw new CompanionError('NATIVE_REQUEST_BUSY');
    this.#busy = true;
    try { return await action(); }
    catch (error) { throw error instanceof CompanionError ? error : new CompanionError('NATIVE_REQUEST_FAILED'); }
    finally { this.#busy = false; }
  }
}
