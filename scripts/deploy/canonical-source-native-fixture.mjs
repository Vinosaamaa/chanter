/** Hosted owning-source proof. Canonical allocation is not complete recovery or provider closure. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { GENESIS, nonzeroUuid, sameWatermark, validatePage } from './terminal-journal.mjs';
import { ScopeVerifier, SCOPE_KINDS } from './deleted-scope.mjs';
import { modules } from './release.mjs';

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
const postgres = original(['ps', '--quiet', 'postgres']).trim();
assert.match(postgres, /^[a-f0-9]{64}$/);
assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', postgres]).trim(), project);
assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.service"}}', postgres]).trim(), 'postgres');
let compiler;
try {
  original(['stop', ...modules, 'frontend', 'livekit']);
  compiler = docker(['create', '--label', `chanter.canonical-fixture=${project}`, release.images['auth-service']]).trim();
  assert.match(compiler, /^[a-f0-9]{64}$/);
  docker(['cp', `${compiler}:/app/lib`, path.join(root, 'lib')]);
  execute('javac', ['-cp', path.join(root, 'lib/*'), '-d', root, 'scripts/deploy/fixtures/CanonicalLifecycleFixture.java']);
  fs.chmodSync(root, 0o755);
  for (const name of fs.readdirSync(root).filter(name => /^CanonicalLifecycleFixture(?:\$[A-Za-z]+)?\.class$/.test(name)))
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
  const jobId = crypto.randomUUID();
  call('auth', { action: 'account-prepare', alias: deletedAlias, jobId }); relay();
  call('auth', { action: 'account-confirm', alias: deletedAlias, jobId }); relay();
  call('media', { action: 'resource-delete', resourceId: available.resourceId, ownerId: owner.accountId }); relay();
  call('community', { action: 'server-delete', serverId: graph.serverId, ownerId: owner.accountId }); relay();
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
    page, scopes, resource: available, byteReadbackVerified: true, deferred, publicCutoverAllowed: false }), { mode: 0o600 });
  console.log('Real source upload/scanner and ACCOUNT/RESOURCE/STUDY_SERVER canonical allocation passed; complete recovery remains pending.');
} finally {
  const failures = [];
  try { original(['stop', 'auth-service', 'community-service', 'media-service']); } catch { failures.push('source stop'); }
  if (compiler) try { docker(['rm', compiler]); } catch { failures.push('compiler removal'); }
  if (failures.length) throw new Error('Canonical fixture cleanup failed: ' + failures.join(', '));
}
