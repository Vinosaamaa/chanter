import { execFile } from 'node:child_process';
import { createHmac, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { lstat, mkdir, open } from 'node:fs/promises';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { CompanionError } from './codex-app-server.mjs';

const run = promisify(execFile);
const id = (value) => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value);

/** Invoke only immediately after exclusive creation; never repair an existing entry. */
export async function ownCreatedEntry(directory, entry) {
  if (process.platform !== 'win32') return;
  await run(path.join(process.env.SystemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe'),
    ['-NoProfile', '-NonInteractive', '-File', fileURLToPath(new URL('./protect-state.ps1', import.meta.url)),
      '-StatePath', directory, '-CreatedEntry', entry], { windowsHide: true, timeout: 10_000, maxBuffer: 4096 });
}

/** Native-selected location only. State contains no provider credentials or course content. */
export class NativeState {
  #db; #secret; #installationId;

  static async open(directory) {
    if (!path.isAbsolute(directory) || path.resolve(directory) === path.parse(directory).root) throw new CompanionError('INVALID_NATIVE_STATE');
    let db;
    try {
      if (process.platform === 'win32') {
        const executable = path.join(process.env.SystemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
        const { stdout } = await run(executable, ['-NoProfile', '-NonInteractive', '-File',
          fileURLToPath(new URL('./protect-state.ps1', import.meta.url)), '-StatePath', directory],
        { windowsHide: true, timeout: 10_000, maxBuffer: 4096 });
        if (stdout !== 'protected') throw new Error();
      } else {
        await mkdir(directory, { mode: 0o700 }).catch((error) => { if (error.code !== 'EEXIST') throw error; });
      }
      for (const name of ['', 'state.sqlite', 'state.sqlite-wal', 'state.sqlite-shm', 'state.sqlite-journal']) {
        let stat;
        try { stat = await lstat(path.join(directory, name)); }
        catch (error) { if (name && error.code === 'ENOENT') continue; throw error; }
        if (stat.isSymbolicLink() || (name ? !stat.isFile() || stat.nlink !== 1 : !stat.isDirectory())
            || (process.platform !== 'win32' && (stat.uid !== process.getuid() || (stat.mode & 0o077) !== 0))) throw new Error();
      }
      // Set the database's creation mode explicitly; SQLite journals inherit its permissions.
      try { const file = await open(path.join(directory, 'state.sqlite'), 'wx', 0o600); await file.close(); await ownCreatedEntry(directory, 'state.sqlite'); }
      catch (error) { if (error.code !== 'EEXIST') throw error; }
      db = new DatabaseSync(path.join(directory, 'state.sqlite'));
      db.exec(`PRAGMA journal_mode=DELETE; PRAGMA synchronous=FULL; PRAGMA busy_timeout=5000;
        CREATE TABLE IF NOT EXISTS installation (singleton INTEGER PRIMARY KEY CHECK(singleton=1), id TEXT NOT NULL, secret BLOB NOT NULL);
        CREATE TABLE IF NOT EXISTS consumed (id TEXT PRIMARY KEY, expires_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS pairing (singleton INTEGER PRIMARY KEY CHECK(singleton=1), token BLOB NOT NULL,
          origin TEXT NOT NULL, user_id TEXT NOT NULL, session_id TEXT NOT NULL, expires_at INTEGER NOT NULL);`);
      db.prepare('INSERT OR IGNORE INTO installation VALUES(1, ?, ?)').run(randomUUID(), randomBytes(32));
      const row = db.prepare('SELECT id, secret FROM installation WHERE singleton=1').get();
      if (!id(row?.id) || row.secret?.length !== 32) throw new Error();
      return new NativeState(db, row);
    } catch {
      db?.close();
      throw new CompanionError('NATIVE_STATE_UNPROTECTED');
    }
  }

  constructor(db, row) { this.#db = db; this.#installationId = row.id; this.#secret = Buffer.from(row.secret); }
  get installationId() { return this.#installationId; }

  /** Consume before transport. Never remove claims to retry uncertain attempts. */
  consume(requestId, expiresAt) {
    if (!id(requestId) || !Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) throw new CompanionError('INVALID_CAPABILITY');
    try {
      this.#db.exec('BEGIN IMMEDIATE');
      if (this.#db.prepare('SELECT 1 FROM consumed WHERE id=?').get(requestId)) throw new CompanionError('CAPABILITY_ALREADY_USED');
      if (this.#db.prepare('SELECT count(*) AS count FROM consumed').get().count >= 10_000) throw new CompanionError('NATIVE_STATE_FULL');
      this.#db.prepare('INSERT INTO consumed VALUES(?, ?)').run(requestId, expiresAt);
      this.#db.exec('COMMIT');
    } catch (error) {
      try { this.#db.exec('ROLLBACK'); } catch { /* State stays unavailable after I/O failure. */ }
      throw error instanceof CompanionError ? error : new CompanionError('NATIVE_STATE_UNAVAILABLE');
    }
  }

  /** Called only after verifying a backend pairing ticket and explicit native approval. */
  pair({ origin, userId, sessionId, expiresAt }) {
    if (!validOrigin(origin) || !id(userId) || !id(sessionId) || !Number.isSafeInteger(expiresAt)
        || expiresAt <= Date.now() || expiresAt > Date.now() + 300_000) throw new CompanionError('INVALID_PAIRING');
    const token = randomBytes(32).toString('base64url');
    try {
      this.#db.prepare('INSERT OR REPLACE INTO pairing VALUES(1, ?, ?, ?, ?, ?)')
        .run(this.#digest(token), origin, userId, sessionId, expiresAt);
      return { handle: token, expiresAt };
    } catch { throw new CompanionError('NATIVE_STATE_UNAVAILABLE'); }
  }

  requirePairing(handle, { origin, userId, sessionId }) {
    let row;
    try { row = this.#db.prepare('SELECT * FROM pairing WHERE singleton=1').get(); }
    catch { throw new CompanionError('NATIVE_STATE_UNAVAILABLE'); }
    if (typeof handle !== 'string' || !/^[a-zA-Z0-9_-]{43}$/.test(handle) || !row || row.expires_at <= Date.now()
        || row.origin !== origin || row.user_id !== userId || row.session_id !== sessionId
        || row.token?.length !== 32 || !timingSafeEqual(this.#digest(handle), row.token)) throw new CompanionError('PAIRING_REQUIRED');
  }

  revokePairing() {
    try { this.#db.prepare('DELETE FROM pairing').run(); }
    catch { throw new CompanionError('NATIVE_STATE_UNAVAILABLE'); }
  }
  close() { this.#db.close(); this.#secret.fill(0); }
  #digest(handle) { return createHmac('sha256', this.#secret).update(handle).digest(); }
}

function validOrigin(value) {
  try { const origin = new URL(value); return origin.protocol === 'https:' && origin.origin === value; }
  catch { return false; }
}
