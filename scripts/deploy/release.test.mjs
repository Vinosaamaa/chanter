import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateRelease, composeFor, planDeployment, executeDeployment, modules, imageNames } from './release.mjs';

const hash = (letter) => `sha256:${letter.repeat(64)}`;
const release = () => ({ version: 1, commit: 'a'.repeat(40), architecture: 'arm64', schemaEpoch: 2,
  images: Object.fromEntries(imageNames.map(name => [name, hash('b')])) });
const config = { environment: 'staging', hostname: 'staging.chanter.example', publicIp: '192.0.2.1' };

test('resource generation writers require epoch 6 and cannot fall back to epoch 5', async () => {
  const policy = JSON.parse(readFileSync(new URL('../../infra/production/release-policy.json', import.meta.url), 'utf8'));
  assert.equal(policy.schemaEpoch, 6);
  const current = { ...release(), schemaEpoch: policy.schemaEpoch };
  const previous = { ...release(), commit: 'c'.repeat(40), schemaEpoch: 5 };
  const actions = [];
  await assert.rejects(executeDeployment(previous, current, async action => actions.push(action)), /schema epoch/);
  assert.deepEqual(actions, []);
  assert.throws(() => planDeployment(previous, current, true), /schema epoch/);
});

test('the public proxy has an exact isolated identity and distributed admission is mandatory', () => {
  const stage = composeFor(release(), config, '/srv/chanter/staging/runtime');
  const prod = composeFor(release(), { ...config, environment: 'production' }, '/srv/chanter/production/runtime');
  const gateway = stage.services['gateway-service'];
  const proxy = stage.services.frontend;
  assert.equal(gateway.environment.CHANTER_EDGE_LIMITS_ENABLED, 'true');
  assert.equal(gateway.environment.CHANTER_TRUSTED_PROXY_ADDRESSES, proxy.networks.edge.ipv4_address);
  assert.equal(gateway.environment.CHANTER_EDGE_PUBLIC_ORIGIN, 'https://staging.chanter.example');
  assert.equal(proxy.networks.application, undefined);
  assert.ok(gateway.networks.application);
  assert.equal(gateway.depends_on.redis.condition, 'service_healthy');
  assert.match(gateway.environment.MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE, /redis/);
  assert.notEqual(stage.networks.edge.ipam.config[0].subnet, prod.networks.edge.ipam.config[0].subnet);
  // Dynamic LiveKit allocation must never consume the proxy's static identity before it starts.
  assert.equal(stage.networks.edge.ipam.config[0].ip_range, '172.30.45.8/29');
  assert.equal(prod.networks.edge.ipam.config[0].ip_range, '172.30.46.8/29');
  assert.ok(!stage.services['auth-service'].networks.includes('edge'));
});

test('release accepts only complete immutable image sets and known architectures', () => {
  assert.doesNotThrow(() => validateRelease(release()));
  const mutable = release(); mutable.images['auth-service'] = 'chanter/auth:latest';
  assert.throws(() => validateRelease(mutable), /immutable/);
  const missing = release(); delete missing.images['auth-service'];
  assert.throws(() => validateRelease(missing), /auth-service/);
  const bad = release(); bad.architecture = 'anything';
  assert.throws(() => validateRelease(bad), /architecture/);
});

test('all applications have resource caps, non-root users and no published internal ports', () => {
  const compose = composeFor(release(), config, '/srv/chanter/staging/runtime');
  for (const name of modules) {
    const service = compose.services[name];
    assert.equal(service.user, '10001:10001');
    assert.equal(service.read_only, true);
    assert.equal(service.environment.SPRING_FLYWAY_ENABLED, 'false');
    assert.ok(service.mem_limit);
    assert.ok(service.healthcheck);
    assert.equal(service.ports, undefined);
  }
  assert.equal(compose.services.minio, undefined);
  assert.equal(compose.services.redpanda, undefined);
  const allocated = Object.values(compose.services).filter(s => !s.profiles).reduce((n, s) => n + Number(s.mem_limit.replace('m', '')), 0);
  assert.ok(allocated <= 10240, `application budget ${allocated} MiB exceeds 10 GiB`);
});

test('production media uses private object storage and an isolated non-root Unix scanner', () => {
  const compose = composeFor(release(), config, '/srv/chanter/staging/runtime');
  const media = compose.services['media-service'];
  const scanner = compose.services.clamav;
  assert.equal(media.environment.CHANTER_MEDIA_STORAGE_BACKEND, 's3');
  assert.equal(media.environment.CHANTER_CLAMAV_TCP_DEVELOPMENT, 'false');
  assert.equal(media.environment.CHANTER_MEDIA_SPOOL_DIR, '/app/media-spool');
  assert.ok(media.volumes.includes('scanner-socket:/run/clamav:ro'));
  assert.ok(media.volumes.includes('resources:/app/resources:ro'));
  assert.equal(scanner.user, '10002:10001');
  assert.equal(scanner.read_only, true);
  assert.equal(scanner.ports, undefined);
  assert.ok(scanner.volumes.includes('scanner-signatures:/var/lib/clamav'));
  assert.ok(!media.volumes.some(volume => volume.startsWith('scanner-signatures:')));
  assert.equal(media.depends_on.clamav.condition, 'service_healthy');
});

test('migrations are isolated one-shot jobs and precede application startup', () => {
  const compose = composeFor(release(), config, '/srv/chanter/staging/runtime');
  assert.deepEqual(compose.services['migrate-auth-service'].command, ['migrate']);
  assert.deepEqual(compose.services['migrate-auth-service'].profiles, ['migration']);
  const plan = planDeployment(release());
  assert.ok(plan.indexOf('migrate') < plan.indexOf('start-applications'));
  assert.ok(plan.indexOf('verify-public-health') < plan.indexOf('record-current'));
});

test('rollback rejects changed schema epochs and persistence images', () => {
  const before = release(); const after = release(); after.commit = 'c'.repeat(40);
  assert.doesNotThrow(() => planDeployment(before, after, true));
  after.schemaEpoch += 1;
  assert.throws(() => planDeployment(before, after, true), /schema/);
  after.schemaEpoch = 2; after.images.postgres = hash('d');
  assert.throws(() => planDeployment(before, after, true), /persistence/);
});

test('normal deployment cannot downgrade a recorded schema epoch', async () => {
  const before = release(); before.schemaEpoch = 4;
  const older = release(); older.commit = 'd'.repeat(40); older.schemaEpoch = 3;
  const steps = [];
  await assert.rejects(executeDeployment(older, before, async operation => steps.push(operation)), /schema epoch/);
  assert.deepEqual(steps, []);
});

test('missing recovery artifacts reject deployment before stopping ingress', async () => {
  const steps = [];
  await assert.rejects(executeDeployment(release(), release(), async operation => {
    steps.push(operation);
    if (operation === 'verify-recovery') throw new Error('Previous bundle is incomplete');
  }), /incomplete/);
  assert.deepEqual(steps, ['verify-images', 'verify-recovery']);
});

test('failed public health restores the prior compatible release and never records the failed release', async () => {
  const before = release(); const after = release(); after.commit = 'c'.repeat(40);
  const steps = [];
  await assert.rejects(executeDeployment(after, before, async (operation, target) => {
    steps.push([operation, target.commit]);
    if (operation === 'verify-public-health' && target.commit === after.commit) throw new Error('unhealthy');
  }), /previous release restored/);
  assert.ok(!steps.some(([operation, commit]) => operation === 'record-current' && commit === after.commit));
  assert.deepEqual(steps.at(-1), ['record-current', before.commit]);
});

test('a failure after ingress starts closes it even when schema rollback is incompatible', async () => {
  const before = release(); const after = release(); after.commit = 'c'.repeat(40); after.schemaEpoch = 3;
  const steps = [];
  await assert.rejects(executeDeployment(after, before, async operation => {
    steps.push(operation);
    if (operation === 'record-current') throw new Error('disk full');
  }), /rollback is incompatible/);
  assert.equal(steps.at(-1), 'stop-ingress');
});
