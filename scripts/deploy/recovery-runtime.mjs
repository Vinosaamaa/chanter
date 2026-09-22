import path from 'node:path';
import { composeFor, validateRelease } from './release.mjs';
import { SOURCES } from './terminal-journal-recovery.mjs';

function protocol(value) {
  if (!value || Object.keys(value).length !== 2 || value.journalSchema !== 2 || value.ordinaryWorkIsolation !== 1)
    throw new Error('Release lacks the required isolated recovery capability');
  return value;
}

export function requireRecoveryCapability(release) { validateRelease(release); protocol(release.recoveryProtocol); }

/** Build-time switch stays disabled until the accepted source union passes native isolation proof. */
export function recoveryCapability(policy) {
  if (!policy || Object.keys(policy).length !== 3 || typeof policy.enabled !== 'boolean') throw new Error('Invalid recovery capability policy');
  const value = protocol({ journalSchema: policy.journalSchema, ordinaryWorkIsolation: policy.ordinaryWorkIsolation });
  return policy.enabled ? { recoveryProtocol: value } : {};
}

/** Pure composition only. The executor must still inspect Docker ownership before stopping or mounting the restored database. */
export function isolatedRecoveryCompose(release, config, runtimeDir, receipt) {
  requireRecoveryCapability(release);
  if (!path.isAbsolute(runtimeDir) || receipt?.version !== 1 || receipt.status !== 'database-restored-isolated'
      || receipt.publicCutoverAllowed !== false || receipt.release !== release.commit || receipt.image !== release.images.postgres
      || !/^chanter-recovery-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(receipt.container)
      || receipt.volume !== `${receipt.container}-data` || receipt.network !== `${receipt.container}-network`)
    throw new Error('Recovery requires the matching isolated database identity');
  const compose = composeFor(release, config, runtimeDir);
  const services = {};
  for (const source of SOURCES) {
    const name = `${source}-service`, service = compose.services[name];
    service.restart = 'no';
    service.env_file = [{ path: path.join(runtimeDir, `${name}.env`), format: 'raw' }];
    service.depends_on = { postgres: { condition: 'service_healthy' } };
    service.networks = ['application'];
    service.labels = { 'chanter.recovery': receipt.container };
    delete service.volumes;
    Object.assign(service.environment, { SERVER_ADDRESS: '127.0.0.1', CHANTER_RECOVERY_MODE: 'true',
      CHANTER_RECOVERY_RESTORE_ID: receipt.container.slice('chanter-recovery-'.length),
      CHANTER_EMAIL_WORKER_ENABLED: 'false', CHANTER_EVENTS_DISPATCH_ENABLED: 'false',
      CHANTER_MEDIA_WORKER_ENABLED: 'false', CHANTER_MEDIA_MIGRATE_LEGACY: 'false',
      CHANTER_INGESTION_WORKER_ENABLED: 'false', CHANTER_LLM_ENABLED: 'false', CHANTER_TELEMETRY_ENABLED: 'false',
      CHANTER_ERRORS_ENABLED: 'false', OTEL_SDK_DISABLED: 'true', OTEL_TRACES_EXPORTER: 'none',
      OTEL_METRICS_EXPORTER: 'none', OTEL_LOGS_EXPORTER: 'none' });
    if (source === 'agent') Object.assign(service.environment, { CHANTER_NATIVE_COMPANION_ORIGIN: '',
      CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8: '', CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI: '', CHANTER_NATIVE_COMPANION_MODELS: '' });
    // Existing media constructors require these paths. Empty scratch is not a restored object store or erasure proof.
    if (source === 'media') service.tmpfs = [...service.tmpfs, ...['/app/resources', '/app/media-spool'].map(directory =>
      `${directory}:size=16m,mode=0700,uid=10001,gid=10001,noexec,nosuid,nodev`)];
    services[name] = service;
  }
  const postgres = compose.services.postgres;
  postgres.restart = 'no'; postgres.env_file = [{ path: path.join(runtimeDir, 'postgres.env'), format: 'raw' }];
  postgres.command = ['postgres', '-c', 'archive_mode=off', '-c', 'listen_addresses=*', '-c', 'shared_buffers=192MB',
    '-c', 'work_mem=2MB', '-c', 'log_statement=none'];
  postgres.volumes = ['postgres:/var/lib/postgresql/data'];
  postgres.labels = { 'chanter.recovery': receipt.container };
  services.postgres = postgres;
  return { name: `chanter-recovery-${receipt.container.slice('chanter-recovery-'.length).replaceAll('-', '')}`, services,
    networks: { application: { name: `${receipt.container}-authority`, internal: true,
      labels: { 'chanter.recovery': receipt.container } } }, volumes: { postgres: { external: true, name: receipt.volume } } };
}
