import { randomBytes } from 'node:crypto';
import { createInterface } from 'node:readline';
import { CompanionError } from './codex-app-server.mjs';

const visible = (value) => JSON.stringify(value).replace(/[\u007f-\u009f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/gu,
  (character) => '\\u' + character.charCodeAt(0).toString(16).padStart(4, '0'));

/** Local interactive terminal only. No HTTP request can answer these challenges. */
export class NativeConsole {
  #lines; #output; #pending = null; #closed = false;
  constructor({ input = process.stdin, output = process.stdout, onCommand }) {
    if (input.isTTY !== true || output.isTTY !== true) throw new CompanionError('INTERACTIVE_TERMINAL_REQUIRED');
    this.#output = output;
    this.#lines = createInterface({ input, terminal: false, crlfDelay: Infinity });
    this.#lines.on('line', (line) => {
      if (line.length > 12_100) { this.say('Command is too long.'); return; }
      if (this.#pending && !['stop', 'restart'].includes(line)) {
        if (line === this.#pending.answer) this.#pending.finish(true);
        else if (line === 'deny') this.#pending.finish(false);
        else this.say('Enter the displayed approval challenge, deny, or stop.');
        return;
      }
      Promise.resolve().then(() => onCommand(line)).catch((error) => this.say(error instanceof CompanionError ? error.code : 'NATIVE_COMMAND_FAILED'));
    });
    this.#lines.on('close', () => { this.#pending?.finish(false); this.#closed = true; });
  }

  say(message) { this.#output.write(String(message).replace(/[\x00-\x09\x0b-\x1f\x7f]/g, '') + '\n'); }

  approve(summary, { signal } = {}) {
    if (this.#closed || signal?.aborted) return Promise.resolve(false);
    if (this.#pending) throw new CompanionError('NATIVE_APPROVAL_BUSY');
    const answer = 'approve ' + randomBytes(6).toString('hex');
    this.say('Native approval requested. Evidence below is untrusted text.');
    for (const [key, value] of Object.entries(summary)) this.say(`${key}: ${visible(value)}`);
    this.say(`Type ${answer} to allow this action, or deny.`);
    return new Promise((resolve) => {
      const abort = () => finish(false);
      const finish = (approved) => {
        if (this.#pending?.answer !== answer) return;
        this.#pending = null; signal?.removeEventListener('abort', abort);
        resolve(approved);
      };
      this.#pending = { answer, finish };
      signal?.addEventListener('abort', abort, { once: true });
    });
  }

  close() { this.#pending?.finish(false); this.#closed = true; this.#lines.close(); }
}
