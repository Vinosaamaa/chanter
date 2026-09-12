#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { modules, databaseModules, validateRelease, validateConfig, composeFor, executeDeployment } from './release.mjs';

const json = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const writeJson = (file, value) => {
  const pending = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(pending, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
  fs.renameSync(pending, file);
};
const secret = () => crypto.randomBytes(32).toString('hex');
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
    if (name === 'realtime-service') values.REDIS_PASSWORD = redis;
    if (['community-service', 'message-service', 'realtime-service'].includes(name)) {
      values.LIVEKIT_API_KEY = mediaKey; values.LIVEKIT_API_SECRET = mediaSecret;
    }
    if (name === 'auth-service') Object.assign(values, { CHANTER_EMAIL_FROM: '', CHANTER_SMTP_HOST: '',
      CHANTER_SMTP_PORT: '587', CHANTER_SMTP_USERNAME: '', CHANTER_SMTP_PASSWORD: '', CHANTER_SMTP_TLS_MODE: 'starttls' });
    save(name, values);
  }
  save('postgres', { POSTGRES_USER: 'chanter_admin', POSTGRES_DB: 'postgres', POSTGRES_PASSWORD: secret(),
    ...Object.fromEntries(databaseModules.map(name => [`DB_${name.replace('-service', '').toUpperCase()}_PASSWORD`, db[name]])) });
  save('redis', { REDIS_PASSWORD: redis });
  save('livekit', { LIVEKIT_KEYS: `${mediaKey}: ${mediaSecret}` });
  writeJson(path.join(stateDir, 'config.json'), config);
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

export function validateRuntime(stateDir) {
  for (const name of [...modules, 'postgres', 'redis', 'livekit']) {
    const file = path.join(stateDir, 'runtime', `${name}.env`);
    if (process.platform !== 'win32' && (fs.statSync(file).mode & 0o077) !== 0) throw new Error(`Runtime file must be private: ${name}.env`);
    const env = readEnv(file);
    const required = name === 'postgres' ? ['POSTGRES_USER', 'POSTGRES_DB', 'POSTGRES_PASSWORD',
      ...databaseModules.map(module => `DB_${module.replace('-service', '').toUpperCase()}_PASSWORD`)]
      : name === 'redis' ? ['REDIS_PASSWORD'] : name === 'livekit' ? ['LIVEKIT_KEYS']
      : ['CHANTER_JWT_SECRET', ...(name === 'gateway-service' ? [] : ['CHANTER_INTERNAL_SERVICE_TOKEN']),
        ...(databaseModules.includes(name) ? ['POSTGRES_PASSWORD'] : []),
        ...(name === 'realtime-service' ? ['REDIS_PASSWORD'] : []),
        ...(['community-service', 'message-service', 'realtime-service'].includes(name) ? ['LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET'] : []),
        ...(name === 'auth-service' ? ['CHANTER_EMAIL_FROM', 'CHANTER_SMTP_HOST', 'CHANTER_SMTP_PORT',
          'CHANTER_SMTP_USERNAME', 'CHANTER_SMTP_PASSWORD', 'CHANTER_SMTP_TLS_MODE'] : [])];
    for (const key of required) if (!env[key]) throw new Error(`Configure ${key} in ${name}.env`);
    for (const [key, value] of Object.entries(env)) if (!value) throw new Error(`Configure ${key} in ${name}.env`);
    if (name === 'auth-service' && !['starttls', 'implicit'].includes(env.CHANTER_SMTP_TLS_MODE)) throw new Error('SMTP requires verified TLS');
    for (const key of ['CHANTER_JWT_SECRET', 'CHANTER_INTERNAL_SERVICE_TOKEN', 'POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'LIVEKIT_API_SECRET']) {
      if (key in env && env[key].length < 32) throw new Error(`Runtime credential too short: ${key}`);
    }
  }
}

export function render(bundleDir, stateDir) {
  const release = validateRelease(json(path.join(bundleDir, 'release.json')));
  const config = validateConfig(json(path.join(stateDir, 'config.json')));
  const output = path.join(stateDir, 'rendered', release.commit);
  fs.mkdirSync(output, { recursive: true, mode: 0o700 });
  writeJson(path.join(output, 'compose.json'), composeFor(release, config, path.join(stateDir, 'runtime')));
  for (const name of ['postgres-init.sh', 'livekit.yaml']) fs.copyFileSync(path.join(bundleDir, 'infra/production', name), path.join(output, name));
  return { release, config, file: path.join(output, 'compose.json') };
}

async function sha256(file) {
  const hash = crypto.createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

export async function verifyPublic(hostname) {
  const base = `https://${hostname}`;
  const request = async (suffix, options = {}) => fetch(base + suffix, { ...options, signal: AbortSignal.timeout(10000), redirect: 'error' });
  const home = await request('/');
  if (home.status !== 200 || !(await home.text()).includes('<html')) throw new Error('Frontend did not serve the application');
  const auth = await request('/api/v1/auth/health');
  if (auth.status !== 200 || (await auth.json()).service !== 'auth-service') throw new Error('Gateway auth route is unhealthy');
  const bootstrap = await request('/api/v1/auth/refresh', { method: 'POST', headers: { Origin: base, 'X-Chanter-CSRF': '1' } });
  if (bootstrap.status !== 204) throw new Error('Secure browser-session bootstrap requires the #242 release');
  const foreign = await request('/api/v1/auth/refresh', { method: 'POST', headers: { Origin: 'https://foreign.invalid', 'X-Chanter-CSRF': '1' } });
  if (foreign.status !== 403) throw new Error('Browser origin protection is missing');
}

function docker(args, capture = false) {
  try { return execFileSync('docker', args, { encoding: 'utf8', stdio: capture ? ['ignore', 'pipe', 'pipe'] : ['ignore', 'inherit', 'inherit'] }); }
  catch { throw new Error(`Docker operation failed: ${args[0]}`); }
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
      } else if (operation === 'stop-ingress') {
        // Reserve the host before the first mutation, including a partially failed first deployment.
        writeJson(activeFile, { environment: prepared.config.environment, stateDir, bundleDir: source });
        compose(['stop', 'frontend']);
      } else if (operation === 'stop-applications') compose(['stop', ...modules, 'livekit']);
      else if (operation === 'start-persistence') compose(['up', '-d', '--wait', '--wait-timeout', '180', 'postgres', 'redis']);
      else if (operation === 'migrate') {
        for (const name of databaseModules) compose(['--profile', 'migration', 'run', '--rm', '--no-deps', `migrate-${name}`]);
      } else if (operation === 'start-applications') {
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
          try { await verifyPublic(prepared.config.hostname); healthy = true; }
          catch { await new Promise(resolve => setTimeout(resolve, 5000)); }
        }
        if (!healthy) { compose(['stop', 'frontend']); throw new Error('Public TLS/API health failed'); }
      } else if (operation === 'record-current') {
        if (current && !recovering) writeJson(path.join(stateDir, 'previous.json'), current);
        writeJson(currentFile, { commit: target.commit, bundleDir: source, completedAt: new Date().toISOString() });
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

async function main(args) {
  const [command, first, second, third, fourth] = args;
  if (command === 'init' && first && second && third && fourth) {
    initialize(path.resolve(first), { environment: second, hostname: third, publicIp: fourth });
    console.log('Environment initialized. Configure SMTP in runtime/auth-service.env before deployment.');
  } else if (command === 'render' && first && second) {
    const result = render(path.resolve(first), path.resolve(second)); console.log(result.file);
  } else if (command === 'deploy' && first && second) await deploy(path.resolve(first), path.resolve(second));
  else if (command === 'rollback' && first) {
    const state = path.resolve(first); const previous = json(path.join(state, 'previous.json'));
    await deploy(previous.bundleDir, state, true);
  } else if (command === 'stop' && first) {
    stopEnvironment(path.resolve(first));
    console.log('Environment stopped; persistent volumes and release receipts retained.');
  } else if (command === 'verify' && first) await verifyPublic(first);
  else throw new Error('Usage: host.mjs init STATE ENV HOST IP | render BUNDLE STATE | deploy BUNDLE STATE | rollback STATE | stop STATE | verify HOST');
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
