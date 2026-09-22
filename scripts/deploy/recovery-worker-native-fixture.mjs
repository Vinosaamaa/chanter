/** Hosted-only worker activation proof. It does not substitute for restored participant data or provider recovery. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { modules } from './release.mjs';
import { isolatedRecoveryCompose } from './recovery-runtime.mjs';
import { SOURCES } from './terminal-journal-recovery.mjs';

if (process.platform !== 'linux' || process.env.CI !== 'true') throw new Error('Hosted native fixture only');
const [bundleDir, state, sourceCompose, sourceProject] = process.argv.slice(2);
assert.match(sourceProject, /^chanter-smoke-(amd64|arm64)-staging-[a-z0-9]{6}$/);
const release = JSON.parse(fs.readFileSync(path.join(bundleDir, 'release.json')));
const config = JSON.parse(fs.readFileSync(path.join(state, 'config.json')));
const token = crypto.randomUUID(), identity = `chanter-recovery-${token}`;
const root = path.resolve('.cache/recovery-worker-proof', token);
fs.mkdirSync(root, { recursive: true, mode: 0o700 });
const execute = (file, args, maximum = 210000) => execFileSync(file, args, { encoding: 'utf8', timeout: maximum,
  maxBuffer: 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
const docker = args => execute('docker', args);
const original = args => docker(['compose', '--project-name', sourceProject, '-f', sourceCompose, ...args]);
const postgres = original(['ps', '--quiet', 'postgres']).trim();
assert.match(postgres, /^[a-f0-9]{64}$/);
assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', postgres]).trim(), sourceProject);
assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.service"}}', postgres]).trim(), 'postgres');
const network = `${identity}-authority`, project = `chanter-recovery-${token.replaceAll('-', '')}`;
const file = path.join(root, 'compose.json');
let compiler, networkCreated = false, connected = false, prepared = false;
const compose = args => docker(['compose', '--project-name', project, '-f', file, ...args]);
try {
  // No original application can change fixture queues while recovery activation is being checked.
  original(['stop', ...modules, 'frontend', 'livekit', 'clamav', 'redis']);
  compiler = docker(['create', '--label', `chanter.recovery=${identity}`, release.images['auth-service']]).trim();
  docker(['cp', `${compiler}:/app/lib`, path.join(root, 'lib')]);
  execute('javac', ['-cp', path.join(root, 'lib/*'), '-d', root, 'scripts/deploy/fixtures/RecoveryApplication.java',
    'infra/production/java/RecoverySchema.java', 'scripts/deploy/fixtures/RecoverySchemaContractTest.java']);
  assert.equal(execute('java', ['-cp', `${root}:${path.join(root, 'lib/*')}`, 'RecoverySchemaContractTest']).trim(),
    'RECOVERY_SCHEMA_CONFIG_VERIFIED');
  fs.writeFileSync(path.join(root, '.dockerignore'), '*\n!Dockerfile\n!RecoveryApplication.class\n');
  fs.writeFileSync(path.join(root, 'Dockerfile'), 'ARG BASE_IMAGE\nFROM ${BASE_IMAGE}\nCOPY --chown=10001:10001 RecoveryApplication.class /app/helpers/\nENTRYPOINT ["java","-cp","/app/helpers:/app/classes:/app/lib/*","RecoveryApplication"]\n');
  const images = { ...release.images };
  for (const source of SOURCES) {
    const name = `${source}-service`, tag = `chanter-recovery-proof-${source}:${token}`;
    // BuildKit interprets a bare sha256 image ID as a registry name in FROM. Pin a verified local tag first.
    const base = `chanter-recovery-base-${source}:${token}`;
    docker(['tag', release.images[name], base]);
    assert.equal(docker(['image', 'inspect', '--format', '{{.Id}}', base]).trim(), release.images[name]);
    docker(['build', '--pull=false', '--network=none', '--build-arg', `BASE_IMAGE=${base}`, '--tag', tag, root]);
    images[name] = docker(['image', 'inspect', '--format', '{{.Id}}', tag]).trim();
  }
  // Fixture-only capability allows testing the new activation boundary before enabling release policy.
  const fixtureRelease = { ...release, images, recoveryProtocol: { journalSchema: 2, ordinaryWorkIsolation: 1 } };
  const receipt = { version: 1, status: 'database-restored-isolated', release: release.commit, image: release.images.postgres,
    container: identity, volume: `${identity}-data`, network: `${identity}-network`, publicCutoverAllowed: false };
  const definition = isolatedRecoveryCompose(fixtureRelease, config, path.join(state, 'runtime'), receipt);
  // This worker-only phase uses the already migrated fixture database. Full restore uses the owning operator command.
  delete definition.services.postgres; delete definition.volumes;
  definition.networks.application = { external: true, name: network };
  for (const service of Object.values(definition.services)) {
    delete service.depends_on;
    service.environment.CHANTER_RECOVERY_FIXTURE = 'true';
  }
  fs.writeFileSync(file, JSON.stringify(definition), { mode: 0o600 });
  docker(['network', 'create', '--internal', '--label', `chanter.recovery=${identity}`, network]); networkCreated = true;
  docker(['network', 'connect', '--alias', 'postgres', network, postgres]); connected = true;
  prepared = true;
  const validateSchema = source => compose(['run', '--rm', '--no-deps', '--entrypoint', 'java', `${source}-service`,
    '-cp', '/app/helpers:/app/classes:/app/lib/*', 'RecoverySchema']).trim();
  for (const source of SOURCES) {
    assert.equal(validateSchema(source), 'RECOVERY_SCHEMA_VERIFIED');
  }
  const sql = statement => original(['exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'chanter_admin',
    '-d', 'chanter_auth', '-Atc', statement]).trim();
  const checksum = sql("SELECT checksum FROM flyway_schema_history WHERE version='1'");
  assert.match(checksum, /^-?[0-9]+$/);
  try {
    sql("UPDATE flyway_schema_history SET checksum=CASE WHEN checksum=0 THEN 1 ELSE 0 END WHERE version='1'");
    assert.throws(() => validateSchema('auth'), 'Changed migration checksum must reject before source startup');
  } finally { sql(`UPDATE flyway_schema_history SET checksum=${checksum} WHERE version='1'`); }
  assert.equal(validateSchema('auth'), 'RECOVERY_SCHEMA_VERIFIED');
  const savedMigration = sql("SELECT row_to_json(history)::text FROM flyway_schema_history history WHERE version='1'");
  assert.equal(JSON.parse(savedMigration).version, '1');
  try {
    sql("DELETE FROM flyway_schema_history WHERE version='1'");
    assert.throws(() => validateSchema('auth'), 'Missing restored migration must reject');
  } finally {
    sql(`INSERT INTO flyway_schema_history SELECT * FROM json_populate_record(NULL::flyway_schema_history, '${savedMigration.replaceAll("'", "''")}')`);
  }
  try {
    sql("INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) "
      + "SELECT MAX(installed_rank)+1,'999999','Recovery fixture','SQL','V999999__recovery_fixture.sql',0,current_user,0,true FROM flyway_schema_history");
    assert.throws(() => validateSchema('auth'), 'A database ahead of the packaged release must reject');
  } finally { sql("DELETE FROM flyway_schema_history WHERE version='999999'"); }
  assert.equal(validateSchema('auth'), 'RECOVERY_SCHEMA_VERIFIED');
  console.log('All packaged source schemas validate; changed, missing and ahead migrations reject without migration');
  for (const source of SOURCES) {
    const name = `${source}-service`;
    try { compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', name]); }
    catch { throw new Error(`Recovery worker fixture failed during ${source} startup`); }
    const id = compose(['ps', '--quiet', name]).trim();
    const output = docker(['logs', '--tail', '100', id]);
    assert.ok(output.includes('RECOVERY_ORDINARY_WORKERS_ABSENT'), 'Actual startup must verify ordinary workers are absent');
    const namespaces = JSON.parse(docker(['inspect', '--format', '{{json .NetworkSettings.Networks}}', id]));
    assert.deepEqual(Object.keys(namespaces), [network]);
    const ports = JSON.parse(docker(['inspect', '--format', '{{json .HostConfig.PortBindings}}', id]));
    assert.equal(Object.keys(ports ?? {}).length, 0);
    console.log(`Recovery mode starts ${source} with no ordinary scheduled worker`);
  }
  assert.equal(docker(['network', 'inspect', '--format', '{{json .Internal}}', network]).trim(), 'true');
  assert.equal(compose(['exec', '-T', 'auth-service', 'java', '-cp', '/app/helpers', 'RecoveryIsolation']).trim(),
    'RECOVERY_SOURCE_LISTENERS_PRIVATE', 'A successful helper must establish container-local source HTTP');
} finally {
  if (prepared) compose(['down', '--remove-orphans']);
  if (connected) docker(['network', 'disconnect', network, postgres]);
  if (networkCreated) {
    assert.equal(docker(['network', 'inspect', '--format', '{{index .Labels "chanter.recovery"}}', network]).trim(), identity);
    docker(['network', 'rm', network]);
  }
  if (compiler) {
    assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "chanter.recovery"}}', compiler]).trim(), identity);
    docker(['rm', compiler]);
  }
}
