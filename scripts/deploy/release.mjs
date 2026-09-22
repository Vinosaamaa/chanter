import path from 'node:path';

export const modules = ['auth-service', 'notification-service', 'community-service', 'message-service',
  'media-service', 'agent-service', 'analytics-service', 'search-service', 'realtime-service', 'gateway-service'];
export const databaseModules = modules.filter(name => !['analytics-service', 'realtime-service', 'gateway-service'].includes(name));
const memory = { 'gateway-service': 384, 'auth-service': 512, 'community-service': 640, 'message-service': 384,
  'realtime-service': 384, 'media-service': 512, 'agent-service': 640, 'analytics-service': 384,
  'search-service': 384, 'notification-service': 384 };
export const imageNames = [...modules, 'frontend', 'postgres', 'redis', 'livekit', 'clamav'];

export function validateRelease(release) {
  if (release.version !== 1 || !/^[a-f0-9]{40}$/.test(release.commit ?? '')) throw new Error('Invalid release version or commit');
  if (!['arm64', 'amd64'].includes(release.architecture)) throw new Error('Unsupported architecture');
  if (!Number.isInteger(release.schemaEpoch) || release.schemaEpoch < 1) throw new Error('Invalid schema epoch');
  if (Object.keys(release.images ?? {}).some(name => !imageNames.includes(name))) throw new Error('Unknown release image');
  for (const name of imageNames) {
    if (!/^sha256:[a-f0-9]{64}$/.test(release.images?.[name] ?? '')) throw new Error(`Missing immutable image ID for ${name}`);
  }
  return release;
}

export function validateConfig(config) {
  if (!['staging', 'production'].includes(config.environment)) throw new Error('Environment must be staging or production');
  if (!/^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/.test(config.hostname ?? '')) {
    throw new Error('Hostname must be a DNS name without protocol, path, credentials or wildcard');
  }
  if (!/^(?:\d{1,3}\.){3}\d{1,3}$/.test(config.publicIp ?? '') || config.publicIp.split('.').some(n => Number(n) > 255)) {
    throw new Error('A valid public IPv4 address is required for LiveKit media');
  }
  return config;
}

export function composeFor(release, config, runtimeDir) {
  validateRelease(release); validateConfig(config);
  const edgePrefix = config.environment === 'production' ? '172.30.46' : '172.30.45';
  const common = { restart: 'unless-stopped', init: true, read_only: true, cap_drop: ['ALL'],
    security_opt: ['no-new-privileges:true'], stop_grace_period: '35s', cpus: '2.0',
    logging: { driver: 'json-file', options: { 'max-size': '10m', 'max-file': '3' } } };
  const services = {};
  const urls = Object.fromEntries(modules.filter(name => name !== 'gateway-service')
    .map(name => [name.replaceAll('-', '_').toUpperCase() + '_URL', `http://${name}:8080`]));
  urls.REALTIME_SERVICE_HTTP_URL = urls.REALTIME_SERVICE_URL;
  urls.REALTIME_SERVICE_URL = 'ws://realtime-service:8080';
  for (const name of modules) {
    const database = databaseModules.includes(name);
    services[name] = { ...common, image: release.images[name], pull_policy: 'never', user: '10001:10001',
      mem_limit: `${memory[name]}m`, tmpfs: ['/tmp:size=64m,mode=1777'],
      env_file: [{ path: path.join(runtimeDir, `${name}.env`), format: 'raw' }, { path: './telemetry.env', format: 'raw' },
        { path: './errors.env', format: 'raw' }],
      environment: { ...urls, SERVER_PORT: '8080', SERVER_SHUTDOWN: 'graceful',
        SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: '25s', SPRING_FLYWAY_ENABLED: 'false',
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: 'health', MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: 'true',
        MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: 'never',
        CHANTER_ENVIRONMENT: config.environment, CHANTER_RELEASE: release.commit,
        LOGGING_STRUCTURED_FORMAT_CONSOLE: 'com.chanter.common.telemetry.SafeLogFormatter',
        OTEL_SERVICE_NAME: name, OTEL_RESOURCE_ATTRIBUTES: `service.version=${release.commit},deployment.environment.name=${config.environment}`,
        MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE: `readinessState${database ? ',db' : name === 'realtime-service' ? ',redis' : ''}`,
        SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: '5', SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: '1',
        SERVER_TOMCAT_THREADS_MAX: '40',
        JAVA_TOOL_OPTIONS: '-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=10 -XX:MaxDirectMemorySize=64m -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError -Xss512k',
        POSTGRES_HOST: 'postgres', POSTGRES_PORT: '5432', POSTGRES_USER: `chanter_${name.replace('-service', '')}`,
        POSTGRES_DB: `chanter_${name.replace('-service', '')}`, REDIS_HOST: 'redis', REDIS_PORT: '6379',
        CHANTER_PUBLIC_BASE_URL: `https://${config.hostname}`, CHANTER_CORS_ORIGINS: `https://${config.hostname}`,
        CHANTER_AUTH_REQUIRE_EMAIL_VERIFICATION: 'true', CHANTER_EMAIL_PROVIDER: 'smtp', CHANTER_EMAIL_LOCAL_SINK: 'false',
        CHANTER_LLM_ENABLED: 'false',
        LIVEKIT_URL: `wss://${config.hostname}/livekit`, LIVEKIT_HTTP_URL: 'http://livekit:7880',
        COURSE_RESOURCE_STORAGE_DIR: '/app/resources' },
      healthcheck: { test: ['CMD', 'java', '-cp', '/app/helpers', 'Probe', 'http://127.0.0.1:8080/actuator/health/readiness'],
        interval: '30s', timeout: '8s', retries: 3, start_period: '120s' },
      networks: ['application'] };
    if (name === 'agent-service') {
      services[name].environment.CHANTER_EMBEDDINGS_MODEL_DIRECTORY = '/app/models/minilm';
    }
    if (database) {
      services[name].depends_on = { postgres: { condition: 'service_healthy' } };
      const migrationEnvironment = { ...services[name].environment };
      if (name === 'agent-service') {
        // Explicit empty values override env_file; Flyway never needs the native issuer.
        Object.assign(migrationEnvironment, { CHANTER_NATIVE_COMPANION_ORIGIN: '',
          CHANTER_NATIVE_COMPANION_PRIVATE_KEY_PKCS8: '', CHANTER_NATIVE_COMPANION_PUBLIC_KEY_SPKI: '',
          CHANTER_NATIVE_COMPANION_MODELS: '' });
      }
      services[`migrate-${name}`] = { image: release.images[name], pull_policy: 'never', user: '10001:10001',
        command: ['migrate'], profiles: ['migration'], restart: 'no', read_only: true, cap_drop: ['ALL'],
        security_opt: ['no-new-privileges:true'], mem_limit: '512m', cpus: '2.0', tmpfs: ['/tmp:size=64m,mode=1777'],
        env_file: services[name].env_file, environment: migrationEnvironment, networks: ['application'] };
    }
  }
  Object.assign(services['media-service'].environment, { CHANTER_MEDIA_STORAGE_BACKEND: 's3',
    CHANTER_MEDIA_SPOOL_DIR: '/app/media-spool', CHANTER_CLAMAV_SOCKET_PATH: '/run/clamav/clamd.sock',
    CHANTER_CLAMAV_TCP_DEVELOPMENT: 'false' });
  services['media-service'].volumes = ['resources:/app/resources:ro', 'media-spool:/app/media-spool',
    'scanner-socket:/run/clamav:ro'];
  services['media-service'].depends_on.clamav = { condition: 'service_healthy' };
  services.clamav = { ...common, image: release.images.clamav, pull_policy: 'never', user: '10002:10001',
    mem_limit: '4096m', environment: { TZ: 'UTC' },
    volumes: ['scanner-signatures:/var/lib/clamav', 'scanner-socket:/run/clamav'],
    tmpfs: ['/tmp:size=128m,mode=1777'],
    healthcheck: { test: ['CMD-SHELL', "printf 'zPING\\000' | nc -w 3 -U /run/clamav/clamd.sock | tr -d '\\000' | grep -qx PONG"],
      interval: '10s', timeout: '5s', retries: 60, start_period: '60s' }, networks: ['application'] };
  services['realtime-service'].depends_on = { redis: { condition: 'service_healthy' } };
  const gateway = services['gateway-service'];
  Object.assign(gateway.environment, { CHANTER_EDGE_LIMITS_ENABLED: 'true',
    CHANTER_TRUSTED_PROXY_ADDRESSES: `${edgePrefix}.2`, CHANTER_EDGE_PUBLIC_ORIGIN: `https://${config.hostname}`,
    MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE: 'readinessState,redis' });
  gateway.depends_on = { redis: { condition: 'service_healthy' } };
  gateway.networks = { application: {}, edge: { ipv4_address: `${edgePrefix}.3` } };
  services.postgres = { ...common, image: release.images.postgres, pull_policy: 'never', user: '70:70',
    mem_limit: '896m', read_only: true, env_file: [{ path: path.join(runtimeDir, 'postgres.env'), format: 'raw' },
      { path: './postgres-backup.env', format: 'raw' }],
    command: ['postgres', '-c', 'max_connections=80', '-c', 'shared_buffers=192MB', '-c', 'work_mem=2MB', '-c', 'log_statement=none',
      '-c', 'archive_mode=on', '-c', 'archive_command=pgbackrest archive-push %p', '-c', 'archive_timeout=60'],
    volumes: ['postgres:/var/lib/postgresql/data', './postgres-init.sh:/docker-entrypoint-initdb.d/01-databases.sh:ro'],
    tmpfs: ['/tmp:size=32m,mode=1777', '/var/run/postgresql:size=16m,mode=1777'],
    healthcheck: { test: ['CMD', 'pg_isready', '-U', 'chanter_admin', '-d', 'postgres'], interval: '10s', timeout: '5s', retries: 10 },
    networks: ['application'] };
  services.redis = { ...common, image: release.images.redis, pull_policy: 'never', user: '999:1000', mem_limit: '192m',
    env_file: [{ path: path.join(runtimeDir, 'redis.env'), format: 'raw' }], volumes: ['redis:/data'], tmpfs: ['/tmp:size=16m,mode=1777'],
    command: ['sh', '-c', 'exec redis-server --requirepass "$$REDIS_PASSWORD" --appendonly yes --maxmemory 96mb --maxmemory-policy noeviction'],
    healthcheck: { test: ['CMD-SHELL', 'REDISCLI_AUTH="$$REDIS_PASSWORD" redis-cli ping | grep -qx PONG'], interval: '10s', timeout: '5s', retries: 10 },
    networks: ['application'] };
  services.livekit = { ...common, image: release.images.livekit, pull_policy: 'never', user: '10001:10001', mem_limit: '256m',
    env_file: [{ path: path.join(runtimeDir, 'livekit.env'), format: 'raw' }], command: ['--config', '/etc/livekit.yaml', '--node-ip', config.publicIp],
    volumes: ['./livekit.yaml:/etc/livekit.yaml:ro'], tmpfs: ['/tmp:size=16m,mode=1777'],
    ports: ['7881:7881/tcp', '7882:7882/udp'], networks: ['application', 'edge'] };
  services.frontend = { ...common, image: release.images.frontend, pull_policy: 'never', user: '10001:10001', mem_limit: '128m',
    environment: { CHANTER_HOSTNAME: config.hostname }, env_file: [{ path: './frontend-errors.env', format: 'raw' }],
    volumes: ['caddy-data:/data', 'caddy-config:/config', './frontend-errors.json:/etc/chanter/frontend-errors.json:ro'],
    ports: ['80:8080', '443:8443'], tmpfs: ['/tmp:size=16m,mode=1777'],
    healthcheck: { test: ['CMD', 'wget', '-q', '--spider', 'http://127.0.0.1:2019/config/'], interval: '15s', timeout: '5s', retries: 10 },
    networks: { edge: { ipv4_address: `${edgePrefix}.2` } } };
  return { name: `chanter-${config.environment}`, services, networks: { application: {},
    edge: { ipam: { config: [{ subnet: `${edgePrefix}.0/28`, ip_range: `${edgePrefix}.8/29` }] } } },
    volumes: Object.fromEntries(['postgres', 'redis', 'resources', 'media-spool', 'scanner-signatures', 'scanner-socket',
      'caddy-data', 'caddy-config'].map(name => [name, {}])) };
}

export function planDeployment(next, current, rollback = false) {
  validateRelease(next);
  if (current) validateRelease(current);
  if (current && next.schemaEpoch < current.schemaEpoch) throw new Error('Deployment cannot downgrade the recorded schema epoch; fix forward');
  if (rollback) {
    if (!current || next.schemaEpoch !== current.schemaEpoch) throw new Error('Rollback requires the same reviewed schema epoch; fix forward');
    if (['postgres', 'redis'].some(name => next.images[name] !== current.images[name])) {
      throw new Error('Rollback cannot change persistence images; fix forward');
    }
  }
  return ['verify-images', ...(current ? ['verify-recovery'] : []), 'stop-ingress', 'stop-applications', 'start-persistence',
    ...(rollback ? [] : ['backup-database', 'migrate']), 'start-applications', 'start-ingress', 'verify-public-health', 'record-current'];
}

export async function executeDeployment(next, current, step, rollback = false) {
  const plan = planDeployment(next, current, rollback);
  let changed = false;
  try {
    for (const operation of plan) {
      if (operation === 'stop-ingress') changed = true;
      await step(operation, next, rollback);
    }
  } catch (error) {
    if (!changed) throw error;
    try { await step('stop-ingress', next, rollback); }
    catch (stopError) { throw new Error('Deployment failed and ingress could not be stopped; operator intervention required.', { cause: stopError }); }
    if (!current || rollback) throw error;
    let recovery;
    try { recovery = planDeployment(current, next, true); }
    catch { throw new Error('Deployment failed; rollback is incompatible. Ingress stays stopped; fix forward.', { cause: error }); }
    try {
      for (const operation of recovery) await step(operation, current, true);
    } catch (recoveryError) {
      try { await step('stop-ingress', current, true); }
      catch (stopError) { throw new Error('Recovery failed and ingress could not be stopped; operator intervention required.', { cause: stopError }); }
      throw new Error('Deployment and rollback failed; operator recovery required.', { cause: recoveryError });
    }
    throw new Error('Deployment failed; previous release restored.', { cause: error });
  }
}
