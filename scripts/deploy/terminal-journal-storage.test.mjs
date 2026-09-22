import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { journalBackupEnvironment, JournalRepository } from './terminal-journal-storage.mjs';
import { GENESIS, entryDigest } from './terminal-journal.mjs';
import { replicateJournal, readCurrentReplica } from './terminal-journal-replica.mjs';
import { scopeStartDigest } from './deleted-scope.mjs';

const settings = { CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.example.test', CHANTER_BACKUP_S3_BUCKET: 'chanter-backups',
  CHANTER_BACKUP_S3_REGION: 'region-1', CHANTER_BACKUP_S3_ACCESS_KEY: 'private-access-canary',
  CHANTER_BACKUP_S3_SECRET_KEY: 'private-secret-canary', CHANTER_BACKUP_CIPHER_PASS: 'd'.repeat(64),
  CHANTER_CONFIG_BACKUP_PASSWORD: 'c'.repeat(64), CHANTER_TERMINAL_JOURNAL_PASSWORD: 'j'.repeat(64) };

export function fixtureBundle(t, binary = null) {
  const parent = path.resolve('.cache/terminal-storage-tests'); fs.mkdirSync(parent, { recursive: true });
  const root = fs.mkdtempSync(path.join(parent, 'run-')); fs.mkdirSync(path.join(root, 'tools'));
  const name = process.platform === 'win32' ? 'restic.exe' : 'restic';
  const bytes = binary ? fs.readFileSync(binary) : Buffer.from('fixture-binary');
  fs.writeFileSync(path.join(root, 'tools', name), bytes, { mode: 0o700 });
  fs.writeFileSync(path.join(root, 'tools/restic.sha256'), crypto.createHash('sha256').update(bytes).digest('hex') + `  ${name}`);
  // Preserve proof files; cleanup is not part of this recovery task.
  return root;
}

test('journal repository requires a distinct secret and fixed encrypted remote prefix', () => {
  const env = journalBackupEnvironment(settings, 'production');
  assert.equal(env.RESTIC_REPOSITORY, 's3:https://backup.example.test/chanter-backups/terminal-journal/production');
  assert.equal(env.RESTIC_PASSWORD, settings.CHANTER_TERMINAL_JOURNAL_PASSWORD);
  assert.equal(env.GOMEMLIMIT, '256MiB');
  for (const password of ['', 'short', settings.CHANTER_BACKUP_CIPHER_PASS, settings.CHANTER_CONFIG_BACKUP_PASSWORD, 'j'.repeat(32) + '\n']) {
    assert.throws(() => journalBackupEnvironment({ ...settings, CHANTER_TERMINAL_JOURNAL_PASSWORD: password }, 'production'));
  }
});

test('journal objects use bounded stdin, full immutable IDs and private execution errors', t => {
  const calls = [], payload = { private: 'target-canary' }, snapshot = 'a'.repeat(64);
  const repository = new JournalRepository({ bundleDir: fixtureBundle(t), environment: 'staging', kind: 'remote',
    env: journalBackupEnvironment(settings, 'staging'), execute: (executable, args, options) => {
      calls.push({ args, options });
      assert.equal(JSON.stringify(args).includes('canary'), false);
      assert.equal(options.env.AWS_SECRET_ACCESS_KEY, settings.CHANTER_BACKUP_S3_SECRET_KEY);
      if (args.includes('backup')) return JSON.stringify({ message_type: 'summary', snapshot_id: snapshot });
      if (args.includes('dump')) return JSON.stringify(payload);
      return '[]';
    } });
  assert.equal(repository.write('page', payload), snapshot);
  assert.deepEqual(repository.read('page', snapshot), payload);
  assert.deepEqual(repository.manifests(), []);
  assert.equal(JSON.parse(calls[0].options.input).private, 'target-canary');
  assert.ok(calls.every(call => call.options.timeout <= 60000 && call.options.maxBuffer <= 16 * 1024 * 1024));
  assert.throws(() => repository.read('page', 'latest'), /reference/);
  assert.throws(() => repository.write('page', { large: 'x'.repeat(512 * 1024) }), /size/);
  const failed = new JournalRepository({ bundleDir: fixtureBundle(t), environment: 'staging', kind: 'remote',
    env: journalBackupEnvironment(settings, 'staging'), execute: () => { throw Error('private-provider-error'); } });
  assert.throws(() => failed.manifests(), /^Error: Encrypted terminal journal storage failed$/);
});

test('real restic round trip encrypts and verifies a complete prefix before checkpointing', { skip: !process.env.CHANTER_TEST_RESTIC }, async t => {
  const bundleDir = fixtureBundle(t, process.env.CHANTER_TEST_RESTIC);
  const env = { RESTIC_REPOSITORY: path.join(bundleDir, 'repository'), RESTIC_PASSWORD: 'fixture-only-secret-'.repeat(3),
    GOMAXPROCS: '1', GOMEMLIMIT: '256MiB', TEMP: bundleDir, TMP: bundleDir, SystemRoot: process.env.SystemRoot ?? '' };
  const repository = new JournalRepository({ bundleDir, environment: 'staging', kind: 'fixture', env });
  repository.initialize();
  const canary = 'terminal-journal-private-target-canary-332';
  const snapshot = repository.write('page', { canary });
  assert.deepEqual(repository.read('page', snapshot), { canary });
  const entry = { revision: 1, eventId: '11111111-1111-4111-8111-111111111111', targetKind: 'STUDY_SERVER',
    targetId: '22222222-2222-4222-8222-222222222222', action: 'DELETE', retentionPolicy: 'PRESERVE_MODERATION_RECORDS_V1', deletedAt: '2026-09-19T06:00:00Z', previousDigest: GENESIS.digest };
  entry.digest = entryDigest(entry);
  const authority = { revision: 1, digest: entry.digest };
  let acknowledged = null;
  await replicateJournal({ kind: 'fixture', checkpoint: async () => null,
    page: async () => ({ schemaVersion: 2, after: GENESIS, through: authority, entries: [entry], next: authority }),
    acknowledge: async value => {
      const verified = readCurrentReplica(repository).manifest;
      assert.deepEqual(verified.authority, authority);
      assert.deepEqual(verified.scopes.map(scope => scope.totalCount), [1, 0]);
      acknowledged = value; return value;
    } }, repository, { kind: 'fixture', scope: async (entry, kind, after) => {
      const ids = kind === 'COURSE' ? ['80000000-0000-0000-0000-000000000000'] : [];
      let scopeDigest = scopeStartDigest(entry.digest, kind, ids.length);
      for (const id of ids) scopeDigest = crypto.createHash('sha256').update(`${scopeDigest}\n${id}\n`).digest('hex');
      return { schemaVersion: 1, studyServerId: entry.targetId, terminalRevision: entry.revision,
        terminalEventId: entry.eventId, terminalDigest: entry.digest, kind, after, totalCount: ids.length,
        scopeDigest, ids, nextAfter: null };
    } });
  assert.equal(acknowledged.revision, 1);
  assert.equal(repository.manifests().length, 1);
  const walk = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? walk(path.join(directory, entry.name)) : [path.join(directory, entry.name)]);
  for (const file of walk(env.RESTIC_REPOSITORY)) assert.equal(fs.readFileSync(file).includes(Buffer.from(canary)), false);
  const wrong = new JournalRepository({ bundleDir, environment: 'staging', kind: 'fixture', env: { ...env, RESTIC_PASSWORD: 'wrong-key' } });
  assert.throws(() => wrong.read('page', snapshot), /^Error: Encrypted terminal journal storage failed$/);
});
