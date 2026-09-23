/** Hosted owning-source proof. Canonical allocation is not complete recovery or provider closure. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { GENESIS, nonzeroUuid, sameWatermark, validatePage } from './terminal-journal.mjs';
import { ScopeVerifier, SCOPE_KINDS } from './deleted-scope.mjs';
import { modules } from './release.mjs';
import { ResourceObjectArchive } from './resource-object-backup.mjs';

assert.equal(process.env.GITHUB_ACTIONS, 'true');
assert.equal(process.env.CHANTER_SOURCE_RECOVERY_PREVIEW, 'true');
const [bundle, state, sourceCompose, project] = process.argv.slice(2);
assert.match(project ?? '', /^chanter-smoke-(amd64|arm64)-[a-z0-9-]+$/);
const release = JSON.parse(fs.readFileSync(path.join(bundle, 'release.json')));
const preview = JSON.parse(fs.readFileSync('.cache/source-recovery-preview.json'));
assert.equal(release.commit, preview.preview);
assert.equal(preview.accepted, false); assert.equal(preview.publicCutoverAllowed, false);
const root = fs.mkdtempSync(path.join(state, 'canonical-source-'));
const composeFile = path.join(root, 'compose.json');
const sources = ['auth', 'community', 'media'];
function execute(file, args, input, maxBuffer = 1024 * 1024) {
  try { return execFileSync(file, args, { input, encoding: 'utf8', timeout: 180_000, maxBuffer,
    stdio: ['pipe', 'pipe', 'pipe'] }); }
  catch { throw new Error('Hosted canonical source command failed'); }
}
const docker = (args, input) => execute('docker', args, input);
const original = args => docker(['compose', '--project-name', project, '-f', sourceCompose, ...args]);
const compose = args => docker(['compose', '--project-name', project, '-f', composeFile, ...args]);
const definition = JSON.parse(fs.readFileSync(sourceCompose));
// The private fixture lives below the original compose directory. Preserve each existing file binding.
for (const service of Object.values(definition.services)) {
  for (const file of service.env_file ?? []) {
    assert.equal(typeof file, 'object');
    file.path = path.resolve(path.dirname(sourceCompose), file.path);
  }
  service.volumes = (service.volumes ?? []).map(volume => {
    assert.equal(typeof volume, 'string');
    if (!volume.startsWith('./') && !volume.startsWith('../')) return volume;
    const separator = volume.indexOf(':'); assert.ok(separator > 0);
    return path.resolve(path.dirname(sourceCompose), volume.slice(0, separator)) + volume.slice(separator);
  });
}
const postgres = original(['ps', '--quiet', 'postgres']).trim();
assert.match(postgres, /^[a-f0-9]{64}$/);
assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', postgres]).trim(), project);
assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.service"}}', postgres]).trim(), 'postgres');
const compilers = [];
let restoreVolume;
try {
  original(['stop', ...modules, 'frontend', 'livekit']);
  const compiler = docker(['create', '--label', `chanter.canonical-fixture=${project}`, release.images['auth-service']]).trim();
  assert.match(compiler, /^[a-f0-9]{64}$/);
  compilers.push(compiler);
  docker(['cp', `${compiler}:/app/lib`, path.join(root, 'lib')]);
  execute('javac', ['-cp', path.join(root, 'lib/*'), '-d', root, 'scripts/deploy/fixtures/CanonicalLifecycleFixture.java']);
  const mediaCompiler = docker(['create', '--label', `chanter.canonical-fixture=${project}`, release.images['media-service']]).trim();
  assert.match(mediaCompiler, /^[a-f0-9]{64}$/); compilers.push(mediaCompiler);
  docker(['cp', `${mediaCompiler}:/app/classes`, path.join(root, 'media-classes')]);
  execute('javac', ['-cp', `${path.join(root, 'media-classes')}:${path.join(root, 'lib/*')}`, '-d', root,
    'scripts/deploy/fixtures/ResourceRecoveryFixture.java']);
  fs.chmodSync(root, 0o755);
  for (const name of fs.readdirSync(root).filter(name => /^(?:CanonicalLifecycleFixture(?:\$[A-Za-z]+)?|ResourceRecoveryFixture)\.class$/.test(name)))
    fs.chmodSync(path.join(root, name), 0o644);
  for (const source of sources) {
    const service = definition.services[`${source}-service`];
    assert.equal(service.image, release.images[`${source}-service`]);
    service.volumes.push(`${root}:/opt/canonical-fixture:ro`);
    Object.assign(service.environment, {
      CHANTER_CANONICAL_LIFECYCLE_FIXTURE: 'true', CHANTER_AUTH_REQUIRE_EMAIL_VERIFICATION: 'false',
      CHANTER_EMAIL_WORKER_ENABLED: 'false', CHANTER_EVENTS_DISPATCH_ENABLED: 'false',
      CHANTER_MEDIA_WORKER_ENABLED: 'false', CHANTER_INGESTION_WORKER_ENABLED: 'false',
      CHANTER_ERRORS_ENABLED: 'false', CHANTER_TELEMETRY_ENABLED: 'false',
      OTEL_TRACES_EXPORTER: 'none', OTEL_METRICS_EXPORTER: 'none',
    });
  }
  fs.writeFileSync(composeFile, JSON.stringify(definition), { mode: 0o600 });
  // The real auth/community processes answer the owning permission clients used by the helper.
  compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'auth-service', 'community-service', 'media-service']);
  const call = (source, request) => {
    assert.ok(sources.includes(source));
    const input = JSON.stringify(request); assert.ok(Buffer.byteLength(input) <= 64 * 1024);
    try {
      return JSON.parse(docker(['compose', '--project-name', project, '-f', composeFile,
        'run', '--rm', '--no-deps', '-T', '--entrypoint', 'java', `${source}-service`,
        '-cp', '/opt/canonical-fixture:/app/classes:/app/lib/*', 'CanonicalLifecycleFixture'], input));
    } catch { throw new Error(`Canonical ${source} action ${request.action} failed`); }
  };
  const cursors = new Map(sources.map(source => [source, 0]));
  const deferred = [];
  let delivered = 0;
  function relay() {
    // Bounded explicit fixture transport. Deferred participants are never counted as complete.
    for (let round = 0; round < 32; round++) {
      let found = false;
      for (const source of sources) {
        const rows = call(source, { action: 'events', afterRevision: cursors.get(source) });
        assert.ok(Array.isArray(rows) && rows.length <= 64);
        for (const row of rows) {
          const event = row.event; nonzeroUuid(event.id);
          assert.equal(event.producer, source); assert.equal(event.schemaVersion, 1);
          assert.ok(Number.isSafeInteger(event.revision) && event.revision > cursors.get(source));
          assert.match(row.destination, /^lifecycle-(auth|community|message|media|agent|notification|search)$/);
          const target = row.destination.slice('lifecycle-'.length);
          if (sources.includes(target)) {
            const result = call(target, { action: 'deliver', event });
            assert.deepEqual(result, { eventId: event.id, committed: true }); delivered++;
          } else deferred.push(row);
          assert.ok(deferred.length + delivered <= 2048);
          cursors.set(source, event.revision); found = true;
        }
      }
      if (!found) return;
    }
    throw new Error('Canonical fixture relay bound exceeded');
  }
  const deletedAlias = crypto.randomUUID(), ownerAlias = crypto.randomUUID();
  const account = call('auth', { action: 'auth-seed', alias: deletedAlias }); nonzeroUuid(account.accountId);
  const owner = call('auth', { action: 'auth-seed', alias: ownerAlias }); nonzeroUuid(owner.accountId);
  const graph = call('community', { action: 'community-seed', ownerId: owner.accountId });
  nonzeroUuid(graph.serverId); nonzeroUuid(graph.courseId);
  const upload = call('media', { action: 'media-upload', courseId: graph.courseId, ownerId: owner.accountId, requestId: crypto.randomUUID() });
  assert.equal(upload.state, 'QUARANTINED'); assert.equal(upload.storageWriteSettled, true);
  const available = call('media', { action: 'media-work-once', resourceId: upload.resourceId });
  assert.equal(available.resourceId, upload.resourceId); assert.equal(available.state, 'AVAILABLE');
  assert.equal(available.storageWriteSettled, true); assert.equal(available.sha256, upload.sha256);
  const readback = call('media', { action: 'media-read-fixture', resourceId: available.resourceId });
  assert.equal(readback.resourceId, available.resourceId); assert.equal(readback.byteSize, available.byteSize);
  assert.equal(readback.sha256, available.sha256);
  assert.ok(typeof readback.base64 === 'string' && readback.base64.length <= 1400);
  const actualBytes = Buffer.from(readback.base64, 'base64');
  assert.equal(actualBytes.toString('base64'), readback.base64);
  assert.equal(actualBytes.length, available.byteSize);
  assert.equal(crypto.createHash('sha256').update(actualBytes).digest('hex'), available.sha256);
  // Own the entire local object namespace before qualifying bytes. This says nothing about an external provider.
  compose(['stop', ...sources.map(source => `${source}-service`)]);
  const mediaContainer = compose(['ps', '--all', '--quiet', 'media-service']).trim();
  const mediaState = JSON.parse(docker(['inspect', mediaContainer]))[0];
  assert.equal(mediaState.State.Running, false);
  const originalMount = mediaState.Mounts.find(mount => mount.Destination === '/app/resources');
  assert.equal(originalMount.Type, 'volume');
  assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "com.docker.compose.project"}}', originalMount.Name]).trim(), project);
  const objectWriters = docker(['ps', '--all', '--quiet', '--filter', `volume=${originalMount.Name}`]).trim().split('\n').filter(Boolean);
  assert.ok(objectWriters.length > 0);
  for (const id of objectWriters) {
    const writer = JSON.parse(docker(['inspect', id]))[0];
    assert.equal(writer.Config.Labels['com.docker.compose.project'], project);
    assert.equal(writer.State.Running, false);
  }
  const objectCall = (request, file = composeFile) => JSON.parse(docker(['compose', '--project-name', project, '-f', file,
    'run', '--rm', '--no-deps', '-T', '-e', 'CHANTER_SOURCE_RECOVERY_PREVIEW=true', '--entrypoint', 'java', 'media-service',
    '-cp', '/opt/canonical-fixture:/app/classes:/app/lib/*', 'ResourceRecoveryFixture'], JSON.stringify(request)));
  const inventoryId = crypto.randomUUID(), databaseBackupId = crypto.randomUUID();
  const fence = objectCall({ action: 'fence', inventoryId });
  assert.equal(fence.unsettledMutations, 0); assert.match(fence.storageNamespaceSha256, /^[a-f0-9]{64}$/);
  const inventory = objectCall({ action: 'capture', inventoryId, databaseBackupId, authority: GENESIS });
  assert.equal(inventory.referenceCount, 1);
  const inventoryPage = objectCall({ action: 'page', inventoryId, authority: GENESIS, after: 0, limit: 16 });
  assert.equal(inventoryPage.nextAfter, null); assert.equal(inventoryPage.references.length, 1);
  const reference = inventoryPage.references[0];
  assert.equal(reference.resourceId, available.resourceId); assert.equal(reference.resourceState, 'AVAILABLE');
  assert.equal(reference.terminal, false); assert.equal(reference.sourceRetained, true);
  const request = { inventoryId, databaseBackupId, authority: GENESIS, ordinal: reference.ordinal };
  const sourceBytes = objectCall({ action: 'read', request });
  assert.deepEqual(Buffer.from(sourceBytes.base64, 'base64'), actualBytes);
  const archive = new ResourceObjectArchive({ bundleDir: bundle, environment: 'staging', kind: 'fixture', env: {
    RESTIC_REPOSITORY: path.join(root, 'object-repository'), RESTIC_PASSWORD: crypto.randomBytes(32).toString('hex'),
    GOMAXPROCS: '1', GOMEMLIMIT: '256MiB',
  } });
  archive.initialize();
  const object = { resourceId: reference.resourceId, courseId: reference.courseId, key: reference.key,
    byteSize: reference.byteSize, sha256: reference.sha256, providerVersionId: reference.providerVersionId,
    disposition: 'EXTANT', storageWriteSettled: true };
  const archived = archive.capture(object, { inventoryId, storageNamespaceSha256: fence.storageNamespaceSha256,
    authority: GENESIS, writers: 'QUIESCENT', unsettledWrites: 0 }, actualBytes);
  const decrypted = archive.readVerified(archived, object, fence.storageNamespaceSha256);
  assert.deepEqual(decrypted, actualBytes);
  // Restore the same logical local namespace into an owned empty volume. No old object is overwritten or removed.
  restoreVolume = `${project}-object-${crypto.randomUUID()}`;
  assert.ok(!docker(['volume', 'ls', '--format', '{{.Name}}']).split('\n').includes(restoreVolume));
  docker(['volume', 'create', '--label', `chanter.object-fixture=${project}`, restoreVolume]);
  const destination = structuredClone(definition);
  destination.volumes.fixture_objects = { external: true, name: restoreVolume };
  let replaced = 0;
  destination.services['media-service'].volumes = destination.services['media-service'].volumes.map(volume => {
    if (typeof volume === 'string' && volume.split(':')[1] === '/app/resources') { replaced++; return 'fixture_objects:/app/resources'; }
    return volume;
  });
  assert.equal(replaced, 1);
  const destinationFile = path.join(root, 'object-restore-compose.json');
  fs.writeFileSync(destinationFile, JSON.stringify(destination), { mode: 0o600 });
  const corrupt = Buffer.from(decrypted); corrupt[0] ^= 1;
  assert.deepEqual(objectCall({ action: 'expect-refusal', request, reason: 'CORRUPT_CONTENT', base64: corrupt.toString('base64') }, destinationFile),
    { refused: 'CORRUPT_CONTENT', publicCutoverAllowed: false });
  const restored = objectCall({ action: 'restore', request, base64: decrypted.toString('base64') }, destinationFile);
  assert.deepEqual(Buffer.from(restored.base64, 'base64'), actualBytes); assert.equal(restored.publicCutoverAllowed, false);
  assert.deepEqual(objectCall({ action: 'expect-refusal', request, reason: 'OBJECT_EXISTS', base64: decrypted.toString('base64') }, destinationFile),
    { refused: 'OBJECT_EXISTS', publicCutoverAllowed: false });
  assert.deepEqual(Buffer.from(objectCall({ action: 'read', request }, destinationFile).base64, 'base64'), actualBytes);
  assert.equal(objectCall({ action: 'fence', inventoryId }).unsettledMutations, 0);
  compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', ...sources.map(source => `${source}-service`)]);
  const jobId = crypto.randomUUID();
  call('auth', { action: 'account-prepare', alias: deletedAlias, jobId }); relay();
  call('auth', { action: 'account-confirm', alias: deletedAlias, jobId }); relay();
  call('media', { action: 'resource-delete', resourceId: available.resourceId, ownerId: owner.accountId }); relay();
  call('community', { action: 'server-delete', serverId: graph.serverId, ownerId: owner.accountId }); relay();
  assert.deepEqual(objectCall({ action: 'expect-refusal', request, reason: 'STALE_SOURCE', base64: decrypted.toString('base64') }, destinationFile),
    { refused: 'STALE_SOURCE', publicCutoverAllowed: false });
  const page = call('auth', { action: 'journal', afterRevision: 0 }); validatePage(page, GENESIS);
  assert.ok(sameWatermark(page.next, page.through));
  assert.deepEqual(page.entries.map(entry => `${entry.targetKind}:${entry.targetId}`).sort(), [
    `ACCOUNT:${account.accountId}`, `RESOURCE:${available.resourceId}`, `STUDY_SERVER:${graph.serverId}`,
  ].sort());
  const server = page.entries.find(entry => entry.targetKind === 'STUDY_SERVER');
  const scopes = [];
  for (const kind of SCOPE_KINDS) {
    const verifier = new ScopeVerifier(server, kind);
    for (let index = 0; !verifier.complete; index++) {
      assert.ok(index < 8, 'Synthetic canonical fixture scope unexpectedly large');
      const scope = call('community', { action: 'current-scope', entry: server, kind, afterId: verifier.after });
      verifier.accept(scope); scopes.push(scope);
    }
    assert.ok(verifier.result().totalCount > 0);
  }
  // Private fixture evidence stays on this disposable host; ordinary source completion is still pending.
  fs.writeFileSync(path.join(root, 'canonical-source.json'), JSON.stringify({ schemaVersion: 1, preview,
    page, scopes, resource: available, inventory, archived, byteReadbackVerified: true, objectRoundtripVerified: true,
    databaseBackupVerified: false, deferred, publicCutoverAllowed: false }), { mode: 0o600 });
  console.log('Real canonical allocation, fenced source inventory and encrypted object roundtrip passed; restored-database recovery remains pending.');
} finally {
  const failures = [];
  try { original(['stop', 'auth-service', 'community-service', 'media-service']); } catch { failures.push('source stop'); }
  for (const compiler of compilers) try { docker(['rm', compiler]); } catch { failures.push('compiler removal'); }
  if (restoreVolume) try {
    assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "chanter.object-fixture"}}', restoreVolume]).trim(), project);
    docker(['volume', 'rm', restoreVolume]);
  } catch { failures.push('fixture object volume removal'); }
  if (failures.length) throw new Error('Canonical fixture cleanup failed: ' + failures.join(', '));
}
