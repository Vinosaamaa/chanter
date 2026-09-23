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
import { sourceDatabaseCheckpoint } from './source-database-native-fixture.mjs';

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
const sources = ['auth', 'community', 'media', 'message', 'agent', 'notification', 'search'];
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
let databaseDrill;
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
          assert.match(row.destination, /^(?:lifecycle-(?:auth|community|message|media|agent|notification|search)|agent|media|message|search|notification)$/);
          const target = row.destination.startsWith('lifecycle-') ? row.destination.slice('lifecycle-'.length) : row.destination;
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
  const liveGraph = call('community', { action: 'community-seed', ownerId: owner.accountId });
  nonzeroUuid(liveGraph.serverId); nonzeroUuid(liveGraph.courseId);
  assert.notEqual(liveGraph.serverId, graph.serverId);
  const questions = liveGraph.channels.filter(channel => channel.kind === 'TEXT' && channel.name === 'questions');
  assert.equal(questions.length, 1);
  const pendingNative = call('agent', { action: 'agent-native-seed', serverId: liveGraph.serverId,
    channelId: questions[0].id, ownerId: owner.accountId });
  nonzeroUuid(pendingNative.requestId); assert.equal(pendingNative.outcome, 'ISSUED');
  assert.equal(pendingNative.syntheticPendingOnly, true);
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
  const liveUpload = call('media', { action: 'media-upload', courseId: liveGraph.courseId,
    ownerId: owner.accountId, requestId: crypto.randomUUID() });
  assert.equal(liveUpload.state, 'QUARANTINED'); assert.equal(liveUpload.storageWriteSettled, true);
  const liveResource = call('media', { action: 'media-work-once', resourceId: liveUpload.resourceId });
  assert.equal(liveResource.state, 'AVAILABLE'); assert.equal(liveResource.storageWriteSettled, true);
  assert.equal(liveResource.byteSize, actualBytes.length); assert.equal(liveResource.sha256, available.sha256);
  const quarantined = call('media', { action: 'media-upload', courseId: liveGraph.courseId,
    ownerId: owner.accountId, requestId: crypto.randomUUID() });
  assert.equal(quarantined.state, 'QUARANTINED'); assert.equal(quarantined.storageWriteSettled, true);
  assert.equal(quarantined.byteSize, actualBytes.length); assert.equal(quarantined.sha256, available.sha256);
  const retainedBytes = liveResource.byteSize + quarantined.byteSize;
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
  assert.equal(inventory.referenceCount, 3);
  const inventoryPage = objectCall({ action: 'page', inventoryId, authority: GENESIS, after: 0, limit: 16 });
  assert.equal(inventoryPage.nextAfter, null); assert.equal(inventoryPage.references.length, 3);
  const reference = inventoryPage.references.find(value => value.resourceId === available.resourceId);
  const liveReference = inventoryPage.references.find(value => value.resourceId === liveResource.resourceId);
  const quarantineReference = inventoryPage.references.find(value => value.resourceId === quarantined.resourceId);
  assert.ok(reference && liveReference && quarantineReference);
  assert.equal(quarantineReference.resourceState, 'QUARANTINED'); assert.equal(quarantineReference.terminal, false);
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
  const liveRequest = { inventoryId, databaseBackupId, authority: GENESIS, ordinal: liveReference.ordinal };
  assert.deepEqual(Buffer.from(objectCall({ action: 'read', request: liveRequest }).base64, 'base64'), actualBytes);
  const liveObject = { resourceId: liveReference.resourceId, courseId: liveReference.courseId, key: liveReference.key,
    byteSize: liveReference.byteSize, sha256: liveReference.sha256, providerVersionId: liveReference.providerVersionId,
    disposition: 'EXTANT', storageWriteSettled: true };
  const liveArchived = archive.capture(liveObject, { inventoryId, storageNamespaceSha256: fence.storageNamespaceSha256,
    authority: GENESIS, writers: 'QUIESCENT', unsettledWrites: 0 }, actualBytes);
  assert.deepEqual(archive.readVerified(liveArchived, liveObject, fence.storageNamespaceSha256), actualBytes);
  const quarantineRequest = { inventoryId, databaseBackupId, authority: GENESIS, ordinal: quarantineReference.ordinal };
  assert.deepEqual(Buffer.from(objectCall({ action: 'read', request: quarantineRequest }).base64, 'base64'), actualBytes);
  const quarantineObject = { resourceId: quarantineReference.resourceId, courseId: quarantineReference.courseId, key: quarantineReference.key,
    byteSize: quarantineReference.byteSize, sha256: quarantineReference.sha256, providerVersionId: quarantineReference.providerVersionId,
    disposition: 'EXTANT', storageWriteSettled: true };
  const quarantineArchived = archive.capture(quarantineObject, { inventoryId, storageNamespaceSha256: fence.storageNamespaceSha256,
    authority: GENESIS, writers: 'QUIESCENT', unsettledWrites: 0 }, actualBytes);
  assert.deepEqual(archive.readVerified(quarantineArchived, quarantineObject, fence.storageNamespaceSha256), actualBytes);
  const leaves = new Map([[available.resourceId, archived], [liveResource.resourceId, liveArchived], [quarantined.resourceId, quarantineArchived]]);
  const inventoryArchive = archive.publishInventory(inventory, { inventoryId, storageNamespaceSha256: fence.storageNamespaceSha256,
    authority: GENESIS, writers: 'QUIESCENT', unsettledWrites: 0 },
  (after, limit) => objectCall({ action: 'page', inventoryId, authority: GENESIS, after, limit }),
  source => leaves.get(source.resourceId));
  assert.equal(archive.verifyInventory(inventoryArchive, inventory).referenceCount, 3);
  databaseDrill = await sourceDatabaseCheckpoint({ bundle, state, root, release, postgres, project,
    composeFile, sourceCompose, inventoryId, databaseBackupId, resourceId: available.resourceId,
    courseId: graph.courseId, nativeRequestId: pendingNative.requestId, liveGraph, liveOwnerId: owner.accountId, inventoryArchive });
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
  const postBackupGraph = call('community', { action: 'community-seed', ownerId: owner.accountId });
  nonzeroUuid(postBackupGraph.serverId); nonzeroUuid(postBackupGraph.courseId);
  const jobId = crypto.randomUUID();
  call('auth', { action: 'account-prepare', alias: deletedAlias, jobId }); relay();
  call('auth', { action: 'account-confirm', alias: deletedAlias, jobId }); relay();
  call('media', { action: 'resource-delete', resourceId: available.resourceId, ownerId: owner.accountId }); relay();
  const resourcePrefix = call('auth', { action: 'journal', afterRevision: 0 }); validatePage(resourcePrefix, GENESIS);
  assert.ok(sameWatermark(resourcePrefix.next, resourcePrefix.through));
  assert.equal(resourcePrefix.entries.length, 2);
  compose(['stop', ...sources.map(source => `${source}-service`)]);
  const applied = objectCall({ action: 'reapply', page: resourcePrefix });
  assert.equal(applied.source, 'media'); assert.equal(applied.schemaVersion, 1);
  assert.ok(sameWatermark(applied.authority, resourcePrefix.through));
  assert.equal(objectCall({ action: 'discard', inventoryId }).inventoryId, inventoryId);
  const terminalInventory = objectCall({ action: 'capture', inventoryId, databaseBackupId, authority: resourcePrefix.through });
  assert.equal(terminalInventory.referenceCount, 3);
  const terminalPage = objectCall({ action: 'page', inventoryId, authority: resourcePrefix.through, after: 0, limit: 16 });
  assert.equal(terminalPage.references.length, 3);
  const terminalReference = terminalPage.references.find(value => value.resourceId === available.resourceId);
  assert.equal(terminalReference.terminal, true);
  const terminalRequest = { inventoryId, databaseBackupId, authority: resourcePrefix.through, ordinal: terminalReference.ordinal };
  assert.deepEqual(objectCall({ action: 'delete', request: terminalRequest }),
    { physicallyClosed: true, outstandingMutations: 0, publicCutoverAllowed: false });
  assert.deepEqual(objectCall({ action: 'finish-delete', request: terminalRequest, resourceId: available.resourceId }),
    { state: 'DELETED', sourceRetained: false, reservedBytes: retainedBytes, publicCutoverAllowed: false });
  // A second completion preserves the live resource's reservation without inventing a worker lease.
  assert.deepEqual(objectCall({ action: 'finish-delete', request: terminalRequest, resourceId: available.resourceId }),
    { state: 'DELETED', sourceRetained: false, reservedBytes: retainedBytes, publicCutoverAllowed: false });
  compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', ...sources.map(source => `${source}-service`)]);
  const historical = call('community', { action: 'community-history-remove-course', serverId: graph.serverId,
    courseId: graph.courseId, ownerId: owner.accountId });
  assert.equal(historical.historicalFixtureOnly, true); assert.equal(historical.courseId, graph.courseId);
  call('community', { action: 'server-delete', serverId: graph.serverId, ownerId: owner.accountId }); relay();
  call('community', { action: 'server-delete', serverId: postBackupGraph.serverId, ownerId: owner.accountId }); relay();
  assert.deepEqual(objectCall({ action: 'expect-refusal', request, reason: 'STALE_SOURCE', base64: decrypted.toString('base64') }, destinationFile),
    { refused: 'STALE_SOURCE', publicCutoverAllowed: false });
  const page = call('auth', { action: 'journal', afterRevision: 0 }); validatePage(page, GENESIS);
  assert.ok(sameWatermark(page.next, page.through));
  assert.deepEqual(page.entries.map(entry => `${entry.targetKind}:${entry.targetId}`).sort(), [
    `ACCOUNT:${account.accountId}`, `RESOURCE:${available.resourceId}`, `STUDY_SERVER:${graph.serverId}`,
    `STUDY_SERVER:${postBackupGraph.serverId}`,
  ].sort());
  assert.ok(!page.entries.some(entry => entry.targetId === liveGraph.serverId || entry.targetId === owner.accountId));
  const scopes = [];
  for (const server of page.entries.filter(entry => entry.targetKind === 'STUDY_SERVER')) for (const kind of SCOPE_KINDS) {
    const verifier = new ScopeVerifier(server, kind);
    for (let index = 0; !verifier.complete; index++) {
      assert.ok(index < 8, 'Synthetic canonical fixture scope unexpectedly large');
      const scope = call('community', { action: 'current-scope', entry: server, kind, afterId: verifier.after });
      verifier.accept(scope); scopes.push(scope);
    }
    // Removing a course preserves server-level channels. Only the removed course graph must be historical.
    if (server.targetId === graph.serverId) {
      if (kind === 'COURSE') assert.equal(verifier.result().totalCount, 0);
      const removed = new Set(kind === 'COURSE' ? [historical.courseId] : historical.channelIds);
      assert.ok(scopes.filter(scope => scope.studyServerId === server.targetId && scope.kind === kind)
        .every(scope => scope.ids.every(id => !removed.has(id))));
    } else assert.ok(verifier.result().totalCount > 0);
  }
  assert.equal(deferred.length, 0, 'Every owning lifecycle command must reach its actual participant');
  const journalReplica = await databaseDrill.archiveCurrent(page.through);
  compose(['stop', ...sources.map(source => `${source}-service`)]);
  const recoveredAuthority = await databaseDrill.recover(page.through, historical, postBackupGraph, {
    archive, liveArchived, liveObject, quarantineArchived, quarantineObject, inventoryArchive, inventory,
    archivedNamespace: fence.storageNamespaceSha256, actualBytes, currentScopes: scopes,
  });
  // Committed delivery is not complete source cleanup or a receipt for replay on a restored database.
  fs.writeFileSync(path.join(root, 'canonical-source.json'), JSON.stringify({ schemaVersion: 1, preview,
    page, scopes, resource: available, inventory, inventoryArchive, objectReferences: [archived, liveArchived, quarantineArchived],
    byteReadbackVerified: true, objectRoundtripVerified: true,
    physicalDeletionVerified: true, databaseBackupVerified: true, journalReplica, recoveredAuthority, historical,
    deferred, publicCutoverAllowed: false }), { mode: 0o600 });
  console.log('Real older-database restore, current authority, historical scope and private object reconstruction passed; external provider and original-writer closure remain pending.');
} finally {
  const failures = [];
  try { databaseDrill?.cleanup(); } catch { failures.push('restored database cleanup'); }
  try { original(['stop', ...sources.map(source => `${source}-service`)]); } catch { failures.push('source stop'); }
  for (const compiler of compilers) try { docker(['rm', compiler]); } catch { failures.push('compiler removal'); }
  if (restoreVolume) try {
    assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "chanter.object-fixture"}}', restoreVolume]).trim(), project);
    docker(['volume', 'rm', restoreVolume]);
  } catch { failures.push('fixture object volume removal'); }
  if (failures.length) throw new Error('Canonical fixture cleanup failed: ' + failures.join(', '));
}
