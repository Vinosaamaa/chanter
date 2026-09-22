import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { initialize, prepareRecovery, readEnv, validateRuntime, stopEnvironment, verifyPublic, verifyMigrationHistory, backupDatabase, render, configurationSnapshot, configurationFingerprint } from './host.mjs';
import { imageNames } from './release.mjs';

const scratch = path.resolve('.cache/deploy-tests');
fs.mkdirSync(scratch, { recursive: true });

test('error receiver changes are preserved in encrypted configuration and invalidate its fingerprint', t => {
  const { state } = fixture(t);
  const release = { commit: 'a'.repeat(40), schemaEpoch: 8 };
  const before = configurationFingerprint(state, release);
  const dsn = `https://${'a'.repeat(32)}@o1.ingest.us.sentry.io/1`;
  fs.writeFileSync(path.join(state, 'runtime/errors.env'), `CHANTER_ERRORS_DSN=${dsn}\n`);
  assert.equal(configurationSnapshot(state, release).runtime.errors.CHANTER_ERRORS_DSN, dsn);
  assert.notEqual(configurationFingerprint(state, release), before);
});

test('missing release receipts cannot authorize an older writer against an orphaned database', t => {
  const { state } = fixture(t);
  const release = { schemaEpoch: 5, commit: 'a'.repeat(40) };
  assert.doesNotThrow(() => verifyMigrationHistory(state, release, 'staging', () => 'chanter-production_postgres\n'));
  assert.throws(() => verifyMigrationHistory(state, release, 'staging', () => 'chanter-staging_postgres\n'), /without migration history/);
  fs.writeFileSync(path.join(state, 'migration-floor.json'), JSON.stringify({ schemaEpoch: 6, commit: 'b'.repeat(40) }));
  assert.throws(() => verifyMigrationHistory(state, release, 'staging', () => { throw new Error('Must reject before Docker'); }), /migration floor/);
});

test('public verification refuses a serving frontend with missing browser security headers', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('<html></html>', { status: 200 }));
  await assert.rejects(verifyPublic('staging.chanter.example'), /security header/);
});

test('optional challenge credentials must be configured together', t => {
  const { state, auth } = fixture(t);
  fs.appendFileSync(auth, 'CHANTER_TURNSTILE_SITE_KEY=fixture-site\n');
  assert.throws(() => validateRuntime(state), /Turnstile/);
  fs.appendFileSync(auth, 'CHANTER_TURNSTILE_SECRET=fixture-secret\n');
  assert.doesNotThrow(() => validateRuntime(state));
});
const fixture = t => {
  const root = fs.mkdtempSync(path.join(scratch, 'host-'));
  t.after(() => {
    assert.ok(path.resolve(root).startsWith(scratch + path.sep));
    fs.rmSync(root, { recursive: true });
  });
  const state = path.join(root, 'staging');
  initialize(state, { environment: 'staging', hostname: 'staging.chanter.example', publicIp: '192.0.2.1' });
  const auth = path.join(state, 'runtime/auth-service.env');
  fs.writeFileSync(auth, fs.readFileSync(auth, 'utf8').replace(/^([A-Z_]+)=$/gm, '$1=fixture-only'));
  const media = path.join(state, 'runtime/media-service.env');
  fs.writeFileSync(media, fs.readFileSync(media, 'utf8').replace(/^([A-Z0-9_]+)=$/gm,
    (_, key) => `${key}=${key === 'CHANTER_S3_ENDPOINT' ? 'https://private-storage.example' : 'fixture-only'}`));
  const backup = path.join(state, 'runtime/backup.env');
  fs.writeFileSync(backup, fs.readFileSync(backup, 'utf8').replace(/^([A-Z0-9_]+)=$/gm,
    (_, key) => `${key}=${key === 'CHANTER_BACKUP_S3_ENDPOINT' ? 'https://backup.example' : 'backup-fixture'}`));
  return { root, state, auth };
};

test('render exposes only the separate browser ingestion key and exact release', t => {
  const { root, state } = fixture(t);
  const bundle = path.join(root, 'bundle');
  fs.mkdirSync(path.join(bundle, 'infra/production'), { recursive: true });
  const release = { version: 1, commit: 'a'.repeat(40), architecture: 'arm64', schemaEpoch: 8,
    images: Object.fromEntries(imageNames.map(name => [name, 'sha256:' + 'b'.repeat(64)])) };
  fs.writeFileSync(path.join(bundle, 'release.json'), JSON.stringify(release));
  for (const name of ['postgres-init.sh', 'livekit.yaml']) fs.writeFileSync(path.join(bundle, 'infra/production', name), 'fixture');
  const backendDsn = `https://${'c'.repeat(32)}@o1.ingest.us.sentry.io/1`;
  const browserDsn = `https://${'d'.repeat(32)}@o2.ingest.us.sentry.io/2`;
  fs.writeFileSync(path.join(state, 'runtime/errors.env'), `CHANTER_ERRORS_DSN=${backendDsn}\nCHANTER_BROWSER_ERRORS_DSN=${browserDsn}\n`);
  const prepared = render(bundle, state);
  const output = path.dirname(prepared.file);
  const publicConfig = fs.readFileSync(path.join(output, 'frontend-errors.json'), 'utf8');
  assert.deepEqual(JSON.parse(publicConfig), { dsn: browserDsn, release: release.commit, environment: 'staging' });
  assert.equal(publicConfig.includes(backendDsn), false);
  assert.equal(readEnv(path.join(output, 'frontend-errors.env')).CHANTER_BROWSER_ERRORS_ORIGIN, 'https://o2.ingest.us.sentry.io');
  const frontend = JSON.parse(fs.readFileSync(prepared.file, 'utf8')).services.frontend;
  assert.equal(JSON.stringify(frontend).includes('errors.env'), true);
  assert.equal(frontend.env_file.some(file => file.path === './errors.env'), false);
  assert.equal(frontend.volumes.includes('./frontend-errors.json:/etc/chanter/frontend-errors.json:ro'), true);
});

test('native signer is absent by default and a complete matching agent-only configuration passes', t => {
  const { state } = fixture(t);
  const file = path.join(state, 'runtime/agent-service.env');
  const before = fs.readFileSync(file, 'utf8');
  assert.ok(!before.includes('CHANTER_NATIVE_COMPANION_'));
  assert.doesNotThrow(() => validateRuntime(state));
  assert.equal(fs.readFileSync(file, 'utf8'), before);
  const pair = crypto.generateKeyPairSync('ed25519');
  const values = {
    CHANTER_NATIVE_COMPANION_ORIGIN: 'https://staging.chanter.example',
    CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8: pair.privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64'),
    CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI: pair.publicKey.export({ format: 'der', type: 'spki' }).toString('base64'),
    CHANTER_NATIVE_COMPANION_MODELS: 'fixture-model, fixture/model:2',
  };
  const text = Object.entries(values).map(([key, value]) => `${key}=${value}`).join('\n') + '\n';
  fs.appendFileSync(file, text);
  assert.doesNotThrow(() => validateRuntime(state));
  assert.equal(fs.readFileSync(file, 'utf8'), before + text);
  const unpadded = Object.entries(values).map(([key, value]) => `${key}=${value.replace(/=+$/, '')}`).join('\n') + '\n';
  fs.writeFileSync(file, before + unpadded);
  assert.doesNotThrow(() => validateRuntime(state));
  assert.equal(fs.readFileSync(file, 'utf8'), before + unpadded);
  const gateway = path.join(state, 'runtime/gateway-service.env');
  assert.ok(!fs.readFileSync(gateway, 'utf8').includes('CHANTER_NATIVE_COMPANION_'));
  fs.appendFileSync(gateway, text);
  assert.throws(() => validateRuntime(state), /only in agent-service/);
});

test('native signer preflight rejects partial, mismatched, malformed and foreign-origin configuration', t => {
  const { state } = fixture(t);
  const file = path.join(state, 'runtime/agent-service.env');
  const before = fs.readFileSync(file, 'utf8');
  const pair = crypto.generateKeyPairSync('ed25519');
  const values = {
    CHANTER_NATIVE_COMPANION_ORIGIN: 'https://staging.chanter.example',
    CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8: pair.privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64'),
    CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI: pair.publicKey.export({ format: 'der', type: 'spki' }).toString('base64'),
    CHANTER_NATIVE_COMPANION_MODELS: 'fixture-model',
  };
  const reject = candidate => {
    fs.writeFileSync(file, before + Object.entries(candidate).map(([key, value]) => `${key}=${value}`).join('\n') + '\n');
    assert.throws(() => validateRuntime(state), /native companion|NATIVE_COMPANION/);
  };
  for (const key of Object.keys(values)) { const partial = { ...values }; delete partial[key]; reject(partial); }
  for (const origin of ['https://foreign.example', 'http://staging.chanter.example', 'https://staging.chanter.example/', 'https://staging.chanter.example:443'])
    reject({ ...values, CHANTER_NATIVE_COMPANION_ORIGIN: origin });
  for (const models of ['', 'bad model', Array.from({ length: 21 }, (_, i) => `model-${i}`).join(',')])
    reject({ ...values, CHANTER_NATIVE_COMPANION_MODELS: models });
  reject({ ...values, CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI: crypto.generateKeyPairSync('ed25519').publicKey.export({ format: 'der', type: 'spki' }).toString('base64') });
  reject({ ...values, CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8: values.CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8 + '!' });
  const ec = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  reject({ ...values, CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8: ec.privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64'),
    CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI: ec.publicKey.export({ format: 'der', type: 'spki' }).toString('base64') });
});

test('initialization isolates credentials and refuses to overwrite existing or partial state', t => {
  const { state } = fixture(t);
  const auth = readEnv(path.join(state, 'runtime/auth-service.env'));
  const gateway = readEnv(path.join(state, 'runtime/gateway-service.env'));
  const media = readEnv(path.join(state, 'runtime/media-service.env'));
  assert.equal(gateway.CHANTER_JWT_SECRET, auth.CHANTER_JWT_SECRET);
  assert.equal(gateway.POSTGRES_PASSWORD, undefined);
  assert.equal(gateway.REDIS_PASSWORD, readEnv(path.join(state, 'runtime/redis.env')).REDIS_PASSWORD);
  assert.ok(gateway.CHANTER_EDGE_KEY_SECRET.length >= 32);
  assert.notEqual(gateway.CHANTER_EDGE_KEY_SECRET, gateway.CHANTER_JWT_SECRET);
  assert.equal(gateway.CHANTER_INTERNAL_SERVICE_TOKEN, undefined);
  assert.equal(media.CHANTER_SMTP_PASSWORD, undefined);
  assert.notEqual(media.POSTGRES_PASSWORD, auth.POSTGRES_PASSWORD);
  const config = JSON.parse(fs.readFileSync(path.join(state, 'config.json')));
  assert.throws(() => initialize(state, config), /initialized/);
  fs.unlinkSync(path.join(state, 'config.json'));
  assert.throws(() => initialize(state, config), /partial/);
});

test('backup credentials remain separate and require configuration before deployment', t => {
  const { state } = fixture(t);
  const file = path.join(state, 'runtime/backup.env');
  const backup = readEnv(file);
  const auth = readEnv(path.join(state, 'runtime/auth-service.env'));
  assert.ok(backup.CHANTER_BACKUP_CIPHER_PASS.length >= 32);
  assert.notEqual(backup.CHANTER_BACKUP_CIPHER_PASS, auth.CHANTER_JWT_SECRET);
  assert.notEqual(backup.CHANTER_CONFIG_BACKUP_PASSWORD, backup.CHANTER_BACKUP_CIPHER_PASS);
  assert.equal(auth.CHANTER_BACKUP_CIPHER_PASS, undefined);
  const configured = fs.readFileSync(file, 'utf8');
  fs.writeFileSync(file, configured.replace(/^CHANTER_BACKUP_S3_ACCESS_KEY=.*$/m, 'CHANTER_BACKUP_S3_ACCESS_KEY=fixture-only'));
  assert.throws(() => validateRuntime(state), /separate bucket and credentials/);
  fs.writeFileSync(file, configured.replace(/^CHANTER_BACKUP_S3_SECRET_KEY=.*$/m, 'CHANTER_BACKUP_S3_SECRET_KEY='));
  assert.throws(() => validateRuntime(state), /required/);
});

test('recovery preparation adds missing settings without rotating any existing secret', t => {
  const { root, state, auth } = fixture(t);
  const authBefore = fs.readFileSync(auth, 'utf8');
  const backup = path.join(state, 'runtime/backup.env');
  const telemetry = path.join(state, 'runtime/telemetry.env');
  const errors = path.join(state, 'runtime/errors.env');
  const original = readEnv(backup);
  fs.writeFileSync(backup, fs.readFileSync(backup, 'utf8').replace(/^CHANTER_CONFIG_BACKUP_PASSWORD=.*\r?\n/m, ''));
  fs.unlinkSync(telemetry);
  fs.unlinkSync(errors);
  prepareRecovery(state);
  assert.equal(readEnv(backup).CHANTER_BACKUP_CIPHER_PASS, original.CHANTER_BACKUP_CIPHER_PASS);
  assert.equal(readEnv(backup).CHANTER_BACKUP_S3_SECRET_KEY, original.CHANTER_BACKUP_S3_SECRET_KEY);
  assert.equal(readEnv(backup).CHANTER_CONFIG_BACKUP_PASSWORD.length, 64);
  assert.equal(readEnv(telemetry).CHANTER_TELEMETRY_ENDPOINT, '');
  assert.equal(readEnv(errors).CHANTER_ERRORS_DSN, '');
  const first = fs.readFileSync(backup, 'utf8');
  prepareRecovery(state);
  assert.equal(fs.readFileSync(backup, 'utf8'), first);
  assert.equal(fs.readFileSync(auth, 'utf8'), authBefore);
  fs.unlinkSync(backup);
  fs.mkdirSync(path.join(root, '.deploy-lock'));
  assert.throws(() => prepareRecovery(state), /active/);
  assert.equal(fs.existsSync(backup), false);
  fs.rmdirSync(path.join(root, '.deploy-lock'));
  prepareRecovery(state);
  assert.equal(readEnv(backup).CHANTER_BACKUP_CIPHER_PASS.length, 64);
  assert.equal(readEnv(backup).CHANTER_BACKUP_S3_ACCESS_KEY, '');
  assert.equal(fs.readFileSync(auth, 'utf8'), authBefore);
});

test('runtime validation requires all credentials and preserves literal SMTP punctuation', t => {
  const { state, auth } = fixture(t);
  assert.doesNotThrow(() => validateRuntime(state));
  fs.appendFileSync(auth, 'UNRELATED_VALUE=$literal#value"with=punctuation\n');
  assert.equal(readEnv(auth).UNRELATED_VALUE, '$literal#value"with=punctuation');
  const gateway = path.join(state, 'runtime/gateway-service.env');
  fs.writeFileSync(gateway, '');
  assert.throws(() => validateRuntime(state), /CHANTER_JWT_SECRET/);
});

test('operator beta policy is explicit and rejects paid modes or unbounded assistant usage', t => {
  const { state } = fixture(t);
  const file = path.join(state, 'runtime/community-service.env');
  const defaults = fs.readFileSync(file, 'utf8');
  assert.equal(readEnv(file).CHANTER_BETA_MODE, 'free_beta');
  assert.equal(readEnv(file).CHANTER_BETA_ASSISTANT_RUN_LIMIT, '1000');
  for (const [key, value] of [['CHANTER_BETA_MODE', 'paid'],
    ['CHANTER_BETA_ASSISTANT_RUN_LIMIT', '0'], ['CHANTER_BETA_ASSISTANT_RUN_LIMIT', '1001'],
    ['CHANTER_BETA_ASSISTANT_RUN_LIMIT', '1.5'], ['CHANTER_BETA_ASSISTANT_RUN_LIMIT', '']]) {
    fs.writeFileSync(file, defaults.replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=${value}`));
    assert.throws(() => validateRuntime(state), /beta|BETA/);
  }
  fs.writeFileSync(file, defaults.replace('CHANTER_BETA_ASSISTANT_RUN_LIMIT=1000', 'CHANTER_BETA_ASSISTANT_RUN_LIMIT=17'));
  assert.doesNotThrow(() => validateRuntime(state));
});

test('deployment requires a private HTTPS object-store configuration before any host mutation', t => {
  const { state } = fixture(t);
  const file = path.join(state, 'runtime/media-service.env');
  const valid = fs.readFileSync(file, 'utf8');
  assert.equal(readEnv(file).CHANTER_S3_ENDPOINT, 'https://private-storage.example');
  for (const endpoint of ['', 'http://private-storage.example', 'https://user:password@private-storage.example',
    'https://private-storage.example?credential=value']) {
    fs.writeFileSync(file, valid.replace(/^CHANTER_S3_ENDPOINT=.*$/m, `CHANTER_S3_ENDPOINT=${endpoint}`));
    assert.throws(() => validateRuntime(state), /S3|object/);
  }
  for (const key of ['CHANTER_S3_BUCKET', 'CHANTER_S3_REGION', 'CHANTER_S3_ACCESS_KEY', 'CHANTER_S3_SECRET_KEY']) {
    fs.writeFileSync(file, valid.replace(new RegExp(`^${key}=.*$`, 'm'), `${key}=`));
    assert.throws(() => validateRuntime(state), /S3/);
  }
  fs.writeFileSync(file, valid);
  assert.doesNotThrow(() => validateRuntime(state));
});

test('a failed first deployment can be stopped without deleting its volumes or losing failure state', t => {
  const { root, state } = fixture(t);
  const bundle = path.join(root, 'bundle');
  fs.mkdirSync(path.join(bundle, 'infra/production'), { recursive: true });
  const release = { version: 1, commit: 'a'.repeat(40), architecture: 'arm64', schemaEpoch: 2,
    images: Object.fromEntries(imageNames.map(name => [name, 'sha256:' + 'b'.repeat(64)])) };
  fs.writeFileSync(path.join(bundle, 'release.json'), JSON.stringify(release));
  for (const name of ['postgres-init.sh', 'livekit.yaml']) fs.writeFileSync(path.join(bundle, 'infra/production', name), 'fixture');
  const marker = path.join(root, 'active-environment.json');
  fs.writeFileSync(marker, JSON.stringify({ environment: 'staging', stateDir: state, bundleDir: bundle }));
  assert.throws(() => stopEnvironment(state, () => { throw new Error('docker failed'); }), /docker failed/);
  assert.equal(fs.existsSync(marker), true);
  const calls = [];
  stopEnvironment(state, args => calls.push(args));
  assert.equal(calls.length, 1);
  assert.equal(calls[0].at(-1), 'stop');
  assert.equal(fs.existsSync(marker), false);
  assert.equal(fs.existsSync(path.join(state, 'runtime/auth-service.env')), true);
});

test('scheduled backup verifies real completion, shares the deployment lock and redacts failure details', t => {
  const { root, state } = fixture(t);
  const bundle = path.join(root, 'bundle');
  fs.mkdirSync(path.join(bundle, 'infra/production'), { recursive: true });
  const release = { version: 1, commit: 'a'.repeat(40), architecture: 'arm64', schemaEpoch: 5,
    images: Object.fromEntries(imageNames.map(name => [name, 'sha256:' + 'b'.repeat(64)])) };
  fs.writeFileSync(path.join(bundle, 'release.json'), JSON.stringify(release));
  for (const name of ['postgres-init.sh', 'livekit.yaml']) fs.writeFileSync(path.join(bundle, 'infra/production', name), 'fixture');
  const prepared = render(bundle, state);
  const renderedBefore = fs.readFileSync(prepared.file, 'utf8');
  fs.writeFileSync(path.join(state, 'current.json'), JSON.stringify({ commit: release.commit, bundleDir: bundle,
    configurationFingerprint: configurationFingerprint(state, release) }));
  fs.writeFileSync(path.join(root, 'active-environment.json'), JSON.stringify({ stateDir: state, bundleDir: bundle }));
  const calls = [];
  const run = args => {
    calls.push(args);
    return JSON.stringify([{ name: 'chanter', status: { code: 0 }, db: [{ id: 1, 'repo-key': 1 }],
      backup: [{ type: 'full', error: false, database: { id: 1, 'repo-key': 1 }, annotation: { 'config-snapshot': 'c'.repeat(64), release: release.commit },
      label: '20260918-000000F', timestamp: { stop: Math.floor(Date.now() / 1000) } }] }]);
  };
  const save = () => ({ snapshotId: 'c'.repeat(64) });
  const verified = [];
  const verify = (...args) => verified.push(args[3]);
  assert.equal(backupDatabase(state, 'full', run, save, verify).status, 'ok');
  assert.deepEqual(verified, ['c'.repeat(64)]);
  assert.throws(() => backupDatabase(state, 'check', run, save, () => { throw new Error('private-config-secret'); }), /verification failed/);
  assert.equal(fs.readFileSync(path.join(state, 'backup-status.json'), 'utf8').includes('private-config-secret'), false);
  calls.splice(3);
  const snapshot = configurationSnapshot(state, release);
  assert.equal(snapshot.runtime.backup, undefined);
  assert.equal(JSON.stringify(snapshot).includes(readEnv(path.join(state, 'runtime/backup.env')).CHANTER_CONFIG_BACKUP_PASSWORD), false);
  assert.deepEqual(calls.map(args => args.at(-1)), ['check', 'backup', 'info']);
  assert.equal(calls.some(args => args.includes('--type=full')), true);
  assert.equal(fs.readFileSync(prepared.file, 'utf8'), renderedBefore);
  const verificationCount = verified.length;
  assert.throws(() => backupDatabase(state, 'check', args => run(args).replaceAll(release.commit, 'b'.repeat(40)), save, verify),
    /verification failed/);
  assert.equal(verified.length, verificationCount, 'an older release snapshot cannot authorize a current backup receipt');
  fs.mkdirSync(path.join(root, '.deploy-lock'));
  assert.throws(() => backupDatabase(state, 'full', run), /active/);
  fs.rmdirSync(path.join(root, '.deploy-lock'));
  assert.throws(() => backupDatabase(state, 'full', () => { throw new Error('private-secret'); }), /verification failed/);
  assert.equal(fs.readFileSync(path.join(state, 'backup-status.json'), 'utf8').includes('private-secret'), false);
  assert.equal(fs.existsSync(path.join(root, '.deploy-lock')), false);
  calls.length = 0;
  fs.appendFileSync(path.join(state, 'runtime/auth-service.env'), 'PENDING_CONFIGURATION_CHANGE=private-unaccepted-value\n');
  assert.throws(() => backupDatabase(state, 'check', run, save, verify), /verification failed/);
  assert.equal(calls.length, 0, 'unaccepted configuration cannot be advertised as matching the running database');
  fs.writeFileSync(path.join(root, 'active-environment.json'), JSON.stringify({ stateDir: state, bundleDir: path.join(root, 'failed-release') }));
  assert.throws(() => backupDatabase(state, 'check', run), /verification failed/);
  assert.equal(calls.length, 0);
});
