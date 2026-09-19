#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { modules, databaseModules, validateRelease, validateConfig, composeFor, executeDeployment } from './release.mjs';
import { assertMigrationFloor, backupEnvironment, backupUnits, summarizeBackup } from './recovery.mjs';
import { telemetryEnvironment } from './telemetry.mjs';
import { configurationBackupEnvironment, runConfigurationBackup, verifyConfigurationBackup } from './configuration-backup.mjs';

const json = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const writePrivateText = (file, value) => {
  const pending = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(pending, value, { mode: 0o600 });
  fs.renameSync(pending, file);
};
const writeJson = (file, value) => writePrivateText(file, JSON.stringify(value, null, 2) + '\n');
const secret = () => crypto.randomBytes(32).toString('hex');
const recoveryDefaults = () => ({
  backup: { CHANTER_BACKUP_S3_ENDPOINT: '', CHANTER_BACKUP_S3_BUCKET: '', CHANTER_BACKUP_S3_REGION: '',
    CHANTER_BACKUP_S3_ACCESS_KEY: '', CHANTER_BACKUP_S3_SECRET_KEY: '', CHANTER_BACKUP_CIPHER_PASS: secret(),
    CHANTER_CONFIG_BACKUP_PASSWORD: secret() },
  telemetry: { CHANTER_TELEMETRY_ENDPOINT: '', CHANTER_TELEMETRY_AUTHORIZATION: '' },
});
const envText = values => Object.entries(values).map(([key, value]) => {
  if (!/^[A-Z][A-Z0-9_]*$/.test(key) || /[\r\n\0]/.test(value)) throw new Error('Invalid environment key or multiline value');
  return `${key}=${value}`;
}).join('\n') + '\n';

export function initialize(stateDir, config) {
  validateConfig(config);
  if (!path.isAbsolute(stateDir)) throw new Error('State directory must be absolute');
  if (fs.existsSync(path.join(stateDir, 'config.json'))) throw new Error('Environment already initialized; refusing to replace secrets');
  if (fs.existsSync(path.join(stateDir, 'runtime'))) throw new Error('Environment contains partial runtime state; inspect it before initializing');
  fs.mkdirSync(path.join(stateDir, 'runtime'), { recursive: true, mode: 0o700 });
  fs.chmodSync(stateDir, 0o700);
  const jwt = secret(); const internal = secret(); const redis = secret(); const mediaKey = secret(); const mediaSecret = secret();
  const db = Object.fromEntries(databaseModules.map(name => [name, secret()]));
  const save = (name, values) => fs.writeFileSync(path.join(stateDir, 'runtime', `${name}.env`), envText(values), { flag: 'wx', mode: 0o600 });
  for (const name of modules) {
    const values = { CHANTER_JWT_SECRET: jwt };
    if (name !== 'gateway-service') values.CHANTER_INTERNAL_SERVICE_TOKEN = internal;
    if (db[name]) values.POSTGRES_PASSWORD = db[name];
    if (['realtime-service', 'gateway-service'].includes(name)) values.REDIS_PASSWORD = redis;
    if (name === 'gateway-service') values.CHANTER_EDGE_KEY_SECRET = secret();
    if (['community-service', 'message-service', 'realtime-service'].includes(name)) {
      values.LIVEKIT_API_KEY = mediaKey; values.LIVEKIT_API_SECRET = mediaSecret;
    }
    if (name === 'auth-service') Object.assign(values, { CHANTER_EMAIL_FROM: '', CHANTER_SMTP_HOST: '',
      CHANTER_SMTP_PORT: '587', CHANTER_SMTP_USERNAME: '', CHANTER_SMTP_PASSWORD: '', CHANTER_SMTP_TLS_MODE: 'starttls' });
    if (name === 'community-service') Object.assign(values, {
      CHANTER_BETA_MODE: 'free_beta', CHANTER_BETA_ASSISTANT_RUN_LIMIT: '1000' });
    if (name === 'media-service') Object.assign(values, { CHANTER_S3_ENDPOINT: '', CHANTER_S3_REGION: '',
      CHANTER_S3_BUCKET: '', CHANTER_S3_ACCESS_KEY: '', CHANTER_S3_SECRET_KEY: '' });
    save(name, values);
  }
  save('postgres', { POSTGRES_USER: 'chanter_admin', POSTGRES_DB: 'postgres', POSTGRES_PASSWORD: secret(),
    ...Object.fromEntries(databaseModules.map(name => [`DB_${name.replace('-service', '').toUpperCase()}_PASSWORD`, db[name]])) });
  save('redis', { REDIS_PASSWORD: redis });
  save('livekit', { LIVEKIT_KEYS: `${mediaKey}: ${mediaSecret}` });
  for (const [name, values] of Object.entries(recoveryDefaults())) save(name, values);
  writeJson(path.join(stateDir, 'config.json'), config);
}

export function prepareRecovery(stateDir) {
  if (!path.isAbsolute(stateDir)) throw new Error('State directory must be absolute');
  validateConfig(json(path.join(stateDir, 'config.json')));
  const runtime = path.join(stateDir, 'runtime');
  if (!fs.lstatSync(runtime).isDirectory() || fs.lstatSync(runtime).isSymbolicLink()) throw new Error('Runtime directory must be owned local state');
  const lock = path.join(path.dirname(stateDir), '.deploy-lock');
  try { fs.mkdirSync(lock); } catch { throw new Error('Deployment or backup is active; inspect the lock before retrying'); }
  try {
    for (const [name, defaults] of Object.entries(recoveryDefaults())) {
      const file = path.join(runtime, `${name}.env`);
      if (!fs.existsSync(file)) { fs.writeFileSync(file, envText(defaults), { flag: 'wx', mode: 0o600 }); continue; }
      const stat = fs.lstatSync(file);
      if (!stat.isFile() || stat.isSymbolicLink() || (process.platform !== 'win32' && (stat.mode & 0o077) !== 0)) {
        throw new Error('Recovery settings must be private regular files');
      }
      const existing = readEnv(file);
      const missing = Object.fromEntries(Object.entries(defaults).filter(([key]) => !Object.hasOwn(existing, key)));
      if (Object.keys(missing).length) writePrivateText(file, fs.readFileSync(file, 'utf8').replace(/\r?\n?$/, '\n') + envText(missing));
    }
  } finally { fs.rmdirSync(lock); }
}

export function readEnv(file) {
  const values = {};
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const at = line.indexOf('=');
    if (at < 1 || !/^[A-Z][A-Z0-9_]*$/.test(line.slice(0, at))) throw new Error('Malformed runtime environment file');
    const name = line.slice(0, at);
    if (name in values) throw new Error(`Duplicate runtime key: ${name}`);
    values[name] = line.slice(at + 1);
  }
  return values;
}

const nativeKeys = ['CHANTER_NATIVE_COMPANION_ORIGIN', 'CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8',
  'CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI', 'CHANTER_NATIVE_COMPANION_MODELS'];

function validateNativeConfiguration(env, origin) {
  try {
    if (nativeKeys.some(key => !env[key]) || env.CHANTER_NATIVE_COMPANION_ORIGIN !== origin) throw new Error();
    const models = new Set(env.CHANTER_NATIVE_COMPANION_MODELS.split(',').map(value => value.trim()).filter(Boolean));
    if (!models.size || models.size > 20 || [...models].some(value => !/^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}$/.test(value))) throw new Error();
    const decode = value => {
      const bytes = Buffer.from(value, 'base64');
      const canonical = bytes.toString('base64');
      if (canonical !== value && canonical.replace(/=+$/, '') !== value) throw new Error();
      return bytes;
    };
    const privateBytes = decode(env.CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8);
    const publicBytes = decode(env.CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI);
    const privateKey = crypto.createPrivateKey({ key: privateBytes, format: 'der', type: 'pkcs8' });
    const publicKey = crypto.createPublicKey({ key: publicBytes, format: 'der', type: 'spki' });
    if (privateKey.asymmetricKeyType !== 'ed25519' || publicKey.asymmetricKeyType !== 'ed25519'
        || !privateKey.export({ format: 'der', type: 'pkcs8' }).equals(privateBytes)
        || !crypto.createPublicKey(privateKey).export({ format: 'der', type: 'spki' }).equals(publicBytes)) throw new Error();
  } catch {
    throw new Error('Optional native companion requires a complete matching Ed25519 configuration for this deployment origin and allowed models');
  }
}

export function validateRuntime(stateDir) {
  const config = validateConfig(json(path.join(stateDir, 'config.json')));
  for (const name of [...modules, 'postgres', 'redis', 'livekit', 'backup', 'telemetry']) {
    const file = path.join(stateDir, 'runtime', `${name}.env`);
    if (process.platform !== 'win32' && (fs.statSync(file).mode & 0o077) !== 0) throw new Error(`Runtime file must be private: ${name}.env`);
    const env = readEnv(file);
    if (name === 'telemetry') { telemetryEnvironment(env); continue; }
    if (name === 'backup') {
      backupEnvironment(env, json(path.join(stateDir, 'config.json')).environment);
      configurationBackupEnvironment(env, json(path.join(stateDir, 'config.json')).environment);
      const media = readEnv(path.join(stateDir, 'runtime/media-service.env'));
      if (env.CHANTER_BACKUP_S3_BUCKET === media.CHANTER_S3_BUCKET
          || env.CHANTER_BACKUP_S3_ACCESS_KEY === media.CHANTER_S3_ACCESS_KEY) {
        throw new Error('Backup storage requires a separate bucket and credentials from resource storage');
      }
      continue;
    }
    const required = name === 'postgres' ? ['POSTGRES_USER', 'POSTGRES_DB', 'POSTGRES_PASSWORD',
      ...databaseModules.map(module => `DB_${module.replace('-service', '').toUpperCase()}_PASSWORD`)]
      : name === 'redis' ? ['REDIS_PASSWORD'] : name === 'livekit' ? ['LIVEKIT_KEYS']
      : ['CHANTER_JWT_SECRET', ...(name === 'gateway-service' ? [] : ['CHANTER_INTERNAL_SERVICE_TOKEN']),
        ...(databaseModules.includes(name) ? ['POSTGRES_PASSWORD'] : []),
        ...(['realtime-service', 'gateway-service'].includes(name) ? ['REDIS_PASSWORD'] : []),
        ...(name === 'gateway-service' ? ['CHANTER_EDGE_KEY_SECRET'] : []),
        ...(name === 'community-service' ? ['CHANTER_BETA_MODE', 'CHANTER_BETA_ASSISTANT_RUN_LIMIT'] : []),
        ...(name === 'media-service' ? ['CHANTER_S3_ENDPOINT', 'CHANTER_S3_REGION', 'CHANTER_S3_BUCKET',
          'CHANTER_S3_ACCESS_KEY', 'CHANTER_S3_SECRET_KEY'] : []),
        ...(['community-service', 'message-service', 'realtime-service'].includes(name) ? ['LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET'] : []),
        ...(name === 'auth-service' ? ['CHANTER_EMAIL_FROM', 'CHANTER_SMTP_HOST', 'CHANTER_SMTP_PORT',
          'CHANTER_SMTP_USERNAME', 'CHANTER_SMTP_PASSWORD', 'CHANTER_SMTP_TLS_MODE'] : [])];
    for (const key of required) if (!env[key]) throw new Error(`Configure ${key} in ${name}.env`);
    for (const [key, value] of Object.entries(env)) if (!value) throw new Error(`Configure ${key} in ${name}.env`);
    if (nativeKeys.some(key => key in env)) {
      if (name !== 'agent-service') throw new Error('Native companion configuration belongs only in agent-service.env');
      validateNativeConfiguration(env, `https://${config.hostname}`);
    }
    if (name === 'auth-service' && !['starttls', 'implicit'].includes(env.CHANTER_SMTP_TLS_MODE)) throw new Error('SMTP requires verified TLS');
    if (name === 'auth-service' && Boolean(env.CHANTER_TURNSTILE_SITE_KEY) !== Boolean(env.CHANTER_TURNSTILE_SECRET)) {
      throw new Error('Optional Turnstile requires both site and secret keys');
    }
    if (name === 'media-service') {
      let endpoint;
      try { endpoint = new URL(env.CHANTER_S3_ENDPOINT); } catch { throw new Error('S3 object storage requires a valid HTTPS endpoint'); }
      if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password || endpoint.search || endpoint.hash) {
        throw new Error('S3 object storage requires HTTPS without embedded credentials, queries or fragments');
      }
    }
    if (name === 'community-service' && (env.CHANTER_BETA_MODE !== 'free_beta'
        || !/^[1-9]\d{0,3}$/.test(env.CHANTER_BETA_ASSISTANT_RUN_LIMIT)
        || Number(env.CHANTER_BETA_ASSISTANT_RUN_LIMIT) > 1000)) {
      throw new Error('Free beta requires a lifetime assistant limit between 1 and 1000');
    }
    for (const key of ['CHANTER_JWT_SECRET', 'CHANTER_INTERNAL_SERVICE_TOKEN', 'POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'LIVEKIT_API_SECRET', 'CHANTER_EDGE_KEY_SECRET']) {
      if (key in env && env[key].length < 32) throw new Error(`Runtime credential too short: ${key}`);
    }
  }
}

export function render(bundleDir, stateDir) {
  const release = validateRelease(json(path.join(bundleDir, 'release.json')));
  const config = validateConfig(json(path.join(stateDir, 'config.json')));
  const output = path.join(stateDir, 'rendered', release.commit);
  fs.mkdirSync(output, { recursive: true, mode: 0o700 });
  writePrivateText(path.join(output, 'postgres-backup.env'), envText(backupEnvironment(
    readEnv(path.join(stateDir, 'runtime/backup.env')), config.environment)));
  writePrivateText(path.join(output, 'telemetry.env'), envText(telemetryEnvironment(readEnv(path.join(stateDir, 'runtime/telemetry.env')))));
  writeJson(path.join(output, 'compose.json'), composeFor(release, config, path.join(stateDir, 'runtime')));
  for (const name of ['postgres-init.sh', 'livekit.yaml']) fs.copyFileSync(path.join(bundleDir, 'infra/production', name), path.join(output, name));
  return { release, config, file: path.join(output, 'compose.json') };
}

async function sha256(file) {
  const hash = crypto.createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

export async function verifyPublic(hostname, stateDir) {
  const base = `https://${hostname}`;
  const request = async (suffix, options = {}) => fetch(base + suffix, { ...options, signal: AbortSignal.timeout(10000), redirect: 'error' });
  const home = await request('/');
  if (home.status !== 200 || !(await home.text()).includes('<html')) throw new Error('Frontend did not serve the application');
  for (const [header, expected] of Object.entries({ 'x-content-type-options': 'nosniff',
    'x-frame-options': 'DENY', 'referrer-policy': 'no-referrer' })) {
    if (home.headers.get(header) !== expected) throw new Error(`Public security header is missing: ${header}`);
  }
  if (!home.headers.get('strict-transport-security')?.includes('max-age=31536000')
      || !home.headers.get('content-security-policy')?.includes("frame-ancestors 'none'")) {
    throw new Error('Public transport or content security policy is missing');
  }
  const auth = await request('/api/v1/auth/health');
  if (auth.status !== 200 || (await auth.json()).service !== 'auth-service') throw new Error('Gateway auth route is unhealthy');
  const bootstrap = await request('/api/v1/auth/refresh', { method: 'POST', headers: { Origin: base, 'X-Chanter-CSRF': '1' } });
  if (bootstrap.status !== 204) throw new Error('Secure browser-session bootstrap requires the #242 release');
  const foreign = await request('/api/v1/auth/refresh', { method: 'POST', headers: { Origin: 'https://foreign.invalid', 'X-Chanter-CSRF': '1' } });
  if (foreign.status !== 403) throw new Error('Browser origin protection is missing');
  if (stateDir) {
    const credentials = readEnv(path.join(stateDir, 'runtime/community-service.env'));
    const now = Math.floor(Date.now() / 1000);
    const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
    const unsigned = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ iss: credentials.LIVEKIT_API_KEY,
      sub: `release-health-${crypto.randomUUID()}`, nbf: now - 10, exp: now + 60,
      video: { roomJoin: true, room: '__chanter_release_health', canPublish: false, canSubscribe: false, canPublishData: false } })}`;
    const token = `${unsigned}.${crypto.createHmac('sha256', credentials.LIVEKIT_API_SECRET).update(unsigned).digest('base64url')}`;
    const endpoint = new URL(`wss://${hostname}/livekit/rtc`);
    endpoint.search = new URLSearchParams({ access_token: token, protocol: '15', sdk: 'js', version: '2.20.0', auto_subscribe: '0' });
    await new Promise((resolve, reject) => {
      const socket = new WebSocket(endpoint);
      const timeout = setTimeout(() => { socket.close(); reject(new Error('Secure LiveKit handshake timed out')); }, 10000);
      socket.addEventListener('open', () => { clearTimeout(timeout); socket.close(); resolve(); }, { once: true });
      socket.addEventListener('error', () => { clearTimeout(timeout); reject(new Error('Secure LiveKit handshake failed')); }, { once: true });
    });
  }
}

function docker(args, capture = false) {
  try { return execFileSync('docker', args, { encoding: 'utf8', stdio: capture ? ['ignore', 'pipe', 'pipe'] : ['ignore', 'inherit', 'inherit'] }); }
  catch { throw new Error(`Docker operation failed: ${args[0]}`); }
}

export function verifyMigrationHistory(stateDir, release, environment, run = docker) {
  const floorFile = path.join(stateDir, 'migration-floor.json');
  const currentFile = path.join(stateDir, 'current.json');
  if (fs.existsSync(floorFile)) assertMigrationFloor(release, json(floorFile));
  if (fs.existsSync(currentFile)) {
    const previous = validateRelease(json(path.join(json(currentFile).bundleDir, 'release.json')));
    assertMigrationFloor(release, previous);
  } else if (!fs.existsSync(floorFile)) {
    // A missing receipt is not proof that the durable database is empty. Do not
    // let a lost state directory silently authorize old application writers.
    const volume = `chanter-${environment}_postgres`;
    const volumes = run(['volume', 'ls', '--format', '{{.Name}}'], true).trim().split(/\r?\n/);
    if (volumes.includes(volume)) throw new Error('Database volume exists without migration history; reconcile recovery state before deployment');
  }
}

export function configurationSnapshot(stateDir, release) {
  const runtime = Object.fromEntries([...modules, 'postgres', 'redis', 'livekit', 'telemetry']
    .map(name => [name, readEnv(path.join(stateDir, 'runtime', `${name}.env`))]));
  const floor = path.join(stateDir, 'migration-floor.json');
  return { version: 1, release, config: json(path.join(stateDir, 'config.json')), runtime,
    migrationFloor: fs.existsSync(floor) ? json(floor) : null };
}

export function configurationFingerprint(stateDir, release) {
  return crypto.createHash('sha256').update(JSON.stringify({ snapshot: configurationSnapshot(stateDir, release),
    backup: readEnv(path.join(stateDir, 'runtime/backup.env')) })).digest('hex');
}

export async function deploy(bundleDir, stateDir, rollback = false) {
  if (process.platform !== 'linux') throw new Error('Host deployment requires Linux; use render and unit tests on other systems');
  validateRuntime(stateDir);
  const majorMinor = docker(['compose', 'version', '--short'], true).trim().replace(/^v/, '').split('.').map(Number);
  if (majorMinor[0] < 2 || (majorMinor[0] === 2 && majorMinor[1] < 30)) throw new Error('Docker Compose 2.30+ is required for raw secret files');
  const prepared = render(bundleDir, stateDir);
  if (/\.(example|test|invalid|localhost)$/.test(prepared.config.hostname)) throw new Error('Deployment requires a real public hostname controlled by the operator');
  if (os.totalmem() < 11 * 1024 ** 3) throw new Error('This ten-service release requires the 12 GB host allocation');
  if (prepared.release.architecture !== (os.arch() === 'arm64' ? 'arm64' : 'amd64')) throw new Error('Release architecture differs from the host');
  const hostRoot = path.dirname(stateDir);
  const activeFile = path.join(hostRoot, 'active-environment.json');
  const lock = path.join(hostRoot, '.deploy-lock');
  try { fs.mkdirSync(lock); } catch { throw new Error('Another deployment is active; inspect the lock before retrying'); }
  try {
    if (fs.existsSync(activeFile) && json(activeFile).stateDir !== stateDir) {
      throw new Error('Another environment owns this host. Explicitly stop it before switching; never run both inside the free VM budget.');
    }
    const floorFile = path.join(stateDir, 'migration-floor.json');
    verifyMigrationHistory(stateDir, prepared.release, prepared.config.environment);
    const checksum = fs.readFileSync(path.join(bundleDir, 'images.sha256'), 'utf8').trim();
    if (!/^[a-f0-9]{64}  images\.tar$/.test(checksum) || (await sha256(path.join(bundleDir, 'images.tar'))) !== checksum.slice(0, 64)) {
      throw new Error('Release image archive checksum mismatch');
    }
    docker(['load', '--input', path.join(bundleDir, 'images.tar')]);
    const currentFile = path.join(stateDir, 'current.json');
    const current = fs.existsSync(currentFile) ? json(currentFile) : null;
    const before = current ? validateRelease(json(path.join(current.bundleDir, 'release.json'))) : null;
    const locations = new Map([[prepared.release.commit, bundleDir], ...(current ? [[before.commit, current.bundleDir]] : [])]);
    await executeDeployment(prepared.release, before, async (operation, target, recovering) => {
      const source = locations.get(target.commit);
      const { file } = render(source, stateDir);
      const compose = args => docker(['compose', '--project-name', `chanter-${prepared.config.environment}`, '-f', file, ...args]);
      process.stdout.write(`${operation}: ${target.commit}\n`);
      if (operation === 'verify-images') {
        for (const id of new Set(Object.values(target.images))) docker(['image', 'inspect', id], true);
        compose(['config', '--quiet']);
      } else if (operation === 'verify-recovery') {
        // Prove the recorded fallback still exists before interrupting the running application.
        const prior = render(current.bundleDir, stateDir);
        for (const id of new Set(Object.values(prior.release.images))) docker(['image', 'inspect', id], true);
        docker(['compose', '-f', prior.file, 'config', '--quiet']);
      } else if (operation === 'stop-ingress') {
        // Reserve the host before the first mutation, including a partially failed first deployment.
        writeJson(activeFile, { environment: prepared.config.environment, stateDir, bundleDir: source });
        compose(['stop', 'frontend']);
      } else if (operation === 'stop-applications') compose(['stop', ...modules, 'livekit', 'clamav']);
      else if (operation === 'start-persistence') compose(['up', '-d', '--wait', '--wait-timeout', '180', 'postgres', 'redis']);
      else if (operation === 'backup-database') {
        const configBackup = runConfigurationBackup(source, readEnv(path.join(stateDir, 'runtime/backup.env')),
          prepared.config.environment, configurationSnapshot(stateDir, target));
        compose(['exec', '-T', 'postgres', 'pgbackrest', 'stanza-create']);
        compose(['exec', '-T', 'postgres', 'pgbackrest', 'check']);
        compose(['exec', '-T', 'postgres', 'pgbackrest', '--type=incr', `--annotation=config-snapshot=${configBackup.snapshotId}`, 'backup']);
      }
      else if (operation === 'migrate') {
        // Persist before the first SQL attempt. A crash must not turn partially migrated
        // data into an apparently empty environment with no successful current.json.
        writeJson(floorFile, { schemaEpoch: target.schemaEpoch, commit: target.commit });
        // pgvector is not a trusted extension. Install as the cluster owner before
        // application-owned Flyway migrations, including already initialized volumes.
        compose(['exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'chanter_admin',
          '-d', 'chanter_agent', '-c', 'CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public']);
        for (const name of databaseModules) compose(['--profile', 'migration', 'run', '--rm', '--no-deps', `migrate-${name}`]);
      } else if (operation === 'start-applications') {
        compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '600', 'clamav']);
        for (const name of modules) compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', name]);
        compose(['up', '-d', '--no-deps', 'livekit']);
        let ready = false;
        for (let attempt = 0; attempt < 15 && !ready; attempt += 1) {
          try { compose(['exec', '-T', 'gateway-service', 'java', '-cp', '/app/helpers', 'Probe', 'http://livekit:7880']); ready = true; }
          catch { await new Promise(resolve => setTimeout(resolve, 1000)); }
        }
        if (!ready) throw new Error('LiveKit signaling is unhealthy');
      } else if (operation === 'start-ingress') compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'frontend']);
      else if (operation === 'verify-public-health') {
        let healthy = false;
        for (let attempt = 0; attempt < 12 && !healthy; attempt += 1) {
          try { await verifyPublic(prepared.config.hostname, stateDir); healthy = true; }
          catch { await new Promise(resolve => setTimeout(resolve, 5000)); }
        }
        if (!healthy) { compose(['stop', 'frontend']); throw new Error('Public TLS/API health failed'); }
      } else if (operation === 'record-current') {
        if (current && !recovering) writeJson(path.join(stateDir, 'previous.json'), current);
        writeJson(currentFile, { commit: target.commit, bundleDir: source, completedAt: new Date().toISOString(),
          configurationFingerprint: configurationFingerprint(stateDir, target) });
      }
    }, rollback);
  } finally { fs.rmdirSync(lock); }
}

export function stopEnvironment(stateDir, run = docker) {
  const hostRoot = path.dirname(stateDir);
  const activeFile = path.join(hostRoot, 'active-environment.json');
  const lock = path.join(hostRoot, '.deploy-lock');
  try { fs.mkdirSync(lock); } catch { throw new Error('Another deployment is active; inspect the lock before retrying'); }
  try {
    const active = fs.existsSync(activeFile) ? json(activeFile) : null;
    if (active && active.stateDir !== stateDir) throw new Error('Another environment owns this host');
    const currentFile = path.join(stateDir, 'current.json');
    const bundle = active?.bundleDir ?? (fs.existsSync(currentFile) ? json(currentFile).bundleDir : null);
    if (!bundle) throw new Error('No deployment receipt is available to stop');
    const { file, config } = render(bundle, stateDir);
    run(['compose', '--project-name', `chanter-${config.environment}`, '-f', file, 'stop']);
    if (active) fs.unlinkSync(activeFile);
  } finally { fs.rmdirSync(lock); }
}

export function backupDatabase(stateDir, type = 'incr', run = docker, saveConfiguration = runConfigurationBackup,
    verifyConfiguration = verifyConfigurationBackup) {
  if (!['full', 'incr', 'check'].includes(type)) throw new Error('Backup type must be full, incr or check');
  const lock = path.join(path.dirname(stateDir), '.deploy-lock');
  try { fs.mkdirSync(lock); } catch { throw new Error('Deployment or backup is active; inspect the lock before retrying'); }
  try {
    const activeFile = path.join(path.dirname(stateDir), 'active-environment.json');
    const currentFile = path.join(stateDir, 'current.json');
    if (!fs.existsSync(activeFile) || !fs.existsSync(currentFile)) throw new Error('Backup requires an active accepted deployment');
    const current = json(currentFile); const active = json(activeFile);
    if (active.stateDir !== stateDir || active.bundleDir !== current.bundleDir) throw new Error('Deployment has not completed; reconcile recovery state before scheduled backup');
    const release = validateRelease(json(path.join(current.bundleDir, 'release.json')));
    if (release.commit !== current.commit) throw new Error('Backup release receipt does not match its bundle');
    const config = validateConfig(json(path.join(stateDir, 'config.json')));
    verifyMigrationHistory(stateDir, release, config.environment, run);
    if (current.configurationFingerprint !== configurationFingerprint(stateDir, release)) {
      throw new Error('Runtime configuration differs from the accepted deployment');
    }
    // Use the accepted container configuration. Rendering here could silently
    // point a running cluster at credentials it has not loaded.
    const file = path.join(stateDir, 'rendered', release.commit, 'compose.json');
    if (!fs.existsSync(file)) throw new Error('Accepted deployment configuration is missing');
    const execute = args => run(['compose', '--project-name', `chanter-${config.environment}`, '-f', file,
      'exec', '-T', 'postgres', 'pgbackrest', ...args], true);
    execute(['check']);
    if (type !== 'check') {
      const snapshot = saveConfiguration(current.bundleDir, readEnv(path.join(stateDir, 'runtime/backup.env')),
        config.environment, configurationSnapshot(stateDir, release));
      execute([`--type=${type}`, `--annotation=release=${release.commit}`, `--annotation=config-snapshot=${snapshot.snapshotId}`, 'backup']);
    }
    const receipt = { status: 'ok', checkedAt: new Date().toISOString(), release: release.commit,
      ...summarizeBackup(JSON.parse(execute(['--output=json', 'info']))) };
    if (receipt.stale) throw new Error('Backup chain is stale');
    verifyConfiguration(current.bundleDir, readEnv(path.join(stateDir, 'runtime/backup.env')),
      config.environment, receipt.configSnapshot);
    writeJson(path.join(stateDir, 'backup-status.json'), receipt);
    return receipt;
  } catch {
    // Keep provider bodies, repository paths, configuration and exception
    // messages out of the timer journal and monitoring receipt.
    writeJson(path.join(stateDir, 'backup-status.json'), { status: 'failed', checkedAt: new Date().toISOString() });
    throw new Error('Database backup verification failed; inspect the private deployment and repository state');
  } finally { fs.rmdirSync(lock); }
}

async function main(args) {
  const [command, first, second, third, fourth] = args;
  if (command === 'init' && first && second && third && fourth) {
    initialize(path.resolve(first), { environment: second, hostname: third, publicIp: fourth });
    console.log('Environment initialized. Configure SMTP, private resource S3 and separate encrypted backup S3 in runtime/auth-service.env, media-service.env and backup.env before deployment.');
  } else if (command === 'render' && first && second) {
    const result = render(path.resolve(first), path.resolve(second)); console.log(result.file);
  } else if (command === 'deploy' && first && second) await deploy(path.resolve(first), path.resolve(second));
  else if (command === 'rollback' && first) {
    const state = path.resolve(first); const previous = json(path.join(state, 'previous.json'));
    await deploy(previous.bundleDir, state, true);
  } else if (command === 'stop' && first) {
    stopEnvironment(path.resolve(first));
    console.log('Environment stopped; persistent volumes and release receipts retained.');
  } else if (command === 'backup' && first) {
    console.log(JSON.stringify(backupDatabase(path.resolve(first), second ?? 'incr')));
  } else if (command === 'prepare-recovery' && first) {
    prepareRecovery(path.resolve(first));
    console.log('Missing recovery settings prepared. Existing runtime secrets retained; configure the private backup repository before deployment.');
  } else if (command === 'init-config-backup' && first && second) {
    const state = path.resolve(second); const config = validateConfig(json(path.join(state, 'config.json')));
    runConfigurationBackup(path.resolve(first), readEnv(path.join(state, 'runtime/backup.env')), config.environment, null, true);
    console.log('Encrypted configuration repository initialized. Keep its password in the offline secret store.');
  } else if (command === 'backup-schedule' && first) {
    const state = path.resolve(first);
    const units = backupUnits(state, process.execPath);
    const directory = path.join(state, 'systemd');
    fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
    writePrivateText(path.join(state, 'backup-runner.mjs'), fs.readFileSync(new URL('./backup-runner.mjs', import.meta.url), 'utf8'));
    for (const [name, contents] of Object.entries(units)) writePrivateText(path.join(directory, name), contents);
    console.log('Backup units prepared in the private state systemd directory. Install and enable them using the recovery runbook.');
  } else if (command === 'verify' && first) await verifyPublic(first, second ? path.resolve(second) : undefined);
  else throw new Error('Usage: host.mjs init STATE ENV HOST IP | prepare-recovery STATE | init-config-backup BUNDLE STATE | render BUNDLE STATE | deploy BUNDLE STATE | rollback STATE | stop STATE | backup STATE [full|incr|check] | backup-schedule STATE | verify HOST [STATE]');
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
