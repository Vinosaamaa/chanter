import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { imageNames } from './release.mjs';
import { isolatedRecoveryCompose, recoveryCapability } from './recovery-runtime.mjs';

const release = () => ({ version: 1, commit: 'a'.repeat(40), schemaEpoch: 8, architecture: 'amd64',
  recoveryProtocol: { journalSchema: 2, ordinaryWorkIsolation: 1 },
  images: Object.fromEntries(imageNames.map(name => [name, 'sha256:' + 'b'.repeat(64)])) });
const config = { environment: 'staging', hostname: 'staging.chanter.example', publicIp: '192.0.2.1' };
const id = 'chanter-recovery-11111111-1111-4111-8111-111111111111';
const receipt = () => ({ version: 1, status: 'database-restored-isolated', release: release().commit,
  image: release().images.postgres, container: id, volume: `${id}-data`, network: `${id}-network`, publicCutoverAllowed: false });

test('historical or incomplete recovery capability fails before any service composition', () => {
  for (const protocol of [undefined, {}, { journalSchema: 1, ordinaryWorkIsolation: 1 },
    { journalSchema: 2, ordinaryWorkIsolation: 0 }, { journalSchema: 2, ordinaryWorkIsolation: 1, extra: true }]) {
    assert.throws(() => isolatedRecoveryCompose({ ...release(), recoveryProtocol: protocol }, config, path.resolve('.cache/runtime'), receipt()), /capability/);
  }
  assert.deepEqual(recoveryCapability({ enabled: false, journalSchema: 2, ordinaryWorkIsolation: 1 }), {});
  assert.deepEqual(recoveryCapability({ enabled: true, journalSchema: 2, ordinaryWorkIsolation: 1 }), { recoveryProtocol: { journalSchema: 2, ordinaryWorkIsolation: 1 } });
});

test('isolated services have no public listeners, external networking, migrations or ordinary workers', () => {
  const compose = isolatedRecoveryCompose(release(), config, path.resolve('.cache/runtime'), receipt());
  assert.equal(Object.keys(compose.services).length, 8);
  assert.equal(compose.networks.application.internal, true);
  assert.deepEqual(compose.volumes.postgres, { external: true, name: `${id}-data` });
  for (const [name, service] of Object.entries(compose.services)) {
    assert.equal(service.ports, undefined);
    assert.equal(service.restart, 'no');
    assert.deepEqual(service.networks, ['application']);
    assert.equal(service.pull_policy, 'never');
    assert.equal(service.env_file.length, 1);
    if (name === 'postgres') continue;
    assert.equal(service.environment.SERVER_ADDRESS, '127.0.0.1');
    assert.equal(service.environment.CHANTER_RECOVERY_MODE, 'true');
    assert.equal(service.environment.SPRING_FLYWAY_ENABLED, 'false');
    for (const flag of ['CHANTER_LLM_ENABLED', 'CHANTER_EVENTS_DISPATCH_ENABLED', 'CHANTER_EMAIL_WORKER_ENABLED',
      'CHANTER_MEDIA_WORKER_ENABLED', 'CHANTER_INGESTION_WORKER_ENABLED', 'CHANTER_TELEMETRY_ENABLED', 'CHANTER_ERRORS_ENABLED']) assert.equal(service.environment[flag], 'false');
    for (const flag of ['OTEL_TRACES_EXPORTER', 'OTEL_METRICS_EXPORTER', 'OTEL_LOGS_EXPORTER']) assert.equal(service.environment[flag], 'none');
  }
  assert.ok(compose.services.postgres.command.includes('archive_mode=off'));
  assert.equal(compose.services['media-service'].volumes, undefined);
  assert.equal(compose.services['agent-service'].environment.CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8, '');
});

test('foreign release, volume identity or a nonisolated database receipt is rejected', () => {
  for (const change of [{ release: 'c'.repeat(40) }, { volume: 'chanter-production_postgres' },
    { container: 'chanter-production-postgres-1' }, { publicCutoverAllowed: true }, { status: 'preparing' }]) {
    assert.throws(() => isolatedRecoveryCompose(release(), config, path.resolve('.cache/runtime'), { ...receipt(), ...change }));
  }
});
