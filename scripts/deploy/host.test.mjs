import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { initialize, readEnv, validateRuntime, stopEnvironment } from './host.mjs';
import { imageNames } from './release.mjs';

const scratch = path.resolve('.cache/deploy-tests');
fs.mkdirSync(scratch, { recursive: true });
const fixture = () => {
  const root = fs.mkdtempSync(path.join(scratch, 'host-'));
  const state = path.join(root, 'staging');
  initialize(state, { environment: 'staging', hostname: 'staging.chanter.example', publicIp: '192.0.2.1' });
  const auth = path.join(state, 'runtime/auth-service.env');
  fs.writeFileSync(auth, fs.readFileSync(auth, 'utf8').replace(/^([A-Z_]+)=$/gm, '$1=fixture-only'));
  return { root, state, auth };
};

test('initialization isolates credentials and refuses to overwrite existing or partial state', () => {
  const { state } = fixture();
  const auth = readEnv(path.join(state, 'runtime/auth-service.env'));
  const gateway = readEnv(path.join(state, 'runtime/gateway-service.env'));
  const media = readEnv(path.join(state, 'runtime/media-service.env'));
  assert.equal(gateway.CHANTER_JWT_SECRET, auth.CHANTER_JWT_SECRET);
  assert.equal(gateway.POSTGRES_PASSWORD, undefined);
  assert.equal(media.CHANTER_SMTP_PASSWORD, undefined);
  assert.notEqual(media.POSTGRES_PASSWORD, auth.POSTGRES_PASSWORD);
  const config = JSON.parse(fs.readFileSync(path.join(state, 'config.json')));
  assert.throws(() => initialize(state, config), /initialized/);
  fs.unlinkSync(path.join(state, 'config.json'));
  assert.throws(() => initialize(state, config), /partial/);
});

test('runtime validation requires all credentials and preserves literal SMTP punctuation', () => {
  const { state, auth } = fixture();
  assert.doesNotThrow(() => validateRuntime(state));
  fs.appendFileSync(auth, 'UNRELATED_VALUE=$literal#value"with=punctuation\n');
  assert.equal(readEnv(auth).UNRELATED_VALUE, '$literal#value"with=punctuation');
  const gateway = path.join(state, 'runtime/gateway-service.env');
  fs.writeFileSync(gateway, '');
  assert.throws(() => validateRuntime(state), /CHANTER_JWT_SECRET/);
});

test('operator beta policy is explicit and rejects paid modes or unbounded assistant usage', () => {
  const { state } = fixture();
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

test('a failed first deployment can be stopped without deleting its volumes or losing failure state', () => {
  const { root, state } = fixture();
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
