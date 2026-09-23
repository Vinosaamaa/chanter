/** Hosted-only PostgreSQL/current-authority drill using the shipped recovery operators. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { configurationSnapshot, readEnv } from './host.mjs';
import { runConfigurationBackup, readConfigurationBackup, verifyConfigurationBackup } from './configuration-backup.mjs';
import { restoreIsolated } from './restore-isolated.mjs';
import { applyRecoveryAuthority } from './restore-current-authority.mjs';
import { JournalRepository } from './terminal-journal-storage.mjs';
import { replicateJournal } from './terminal-journal-replica.mjs';
import { lifecycleClient } from './terminal-journal-client.mjs';
import { checkpointIdentity, nonzeroUuid, sameWatermark } from './terminal-journal.mjs';
import { withResourceRecoveryHttp } from './resource-object-http-native-fixture.mjs';

const execute = (file, args, options = {}) => {
  try { return execFileSync(file, args, { encoding: 'utf8', timeout: 600_000,
    maxBuffer: 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'], ...options }); }
  catch { throw new Error('Hosted database fixture command failed'); }
};
const docker = args => execute('docker', args);
const json = file => JSON.parse(fs.readFileSync(file));
const write = (file, value) => fs.writeFileSync(file, JSON.stringify(value), { mode: 0o600 });

export async function sourceDatabaseCheckpoint({ bundle, state, root, release, postgres, project, composeFile, sourceCompose,
  inventoryId, databaseBackupId, resourceId, courseId, nativeRequestId, liveGraph, liveOwnerId, inventoryArchive }) {
  assert.equal(process.env.GITHUB_ACTIONS, 'true');
  assert.equal(process.env.CHANTER_SOURCE_RECOVERY_PREVIEW, 'true');
  assert.match(project, /^chanter-smoke-(amd64|arm64)-[a-z0-9-]+$/);
  for (const id of [inventoryId, databaseBackupId, resourceId, courseId, nativeRequestId,
    liveGraph.serverId, liveGraph.courseId, liveOwnerId]) nonzeroUuid(id);
  assert.equal(inventoryArchive.snapshot.inventoryId, inventoryId);
  assert.equal(inventoryArchive.snapshot.databaseBackupId, databaseBackupId);
  assert.match(inventoryArchive.manifest.snapshotId, /^[a-f0-9]{64}$/);
  const original = JSON.parse(docker(['inspect', postgres]))[0];
  assert.equal(original.Config.Labels['com.docker.compose.project'], project);
  assert.equal(original.Config.Labels['com.docker.compose.service'], 'postgres');
  const repositoryMount = original.Mounts.find(value => value.Destination === '/var/lib/pgbackrest/repo');
  assert.equal(repositoryMount?.Type, 'volume');
  assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "com.docker.compose.project"}}', repositoryMount.Name]).trim(), project);
  const fixtureBundle = path.join(root, 'database-bundle');
  fs.mkdirSync(path.join(fixtureBundle, 'tools'), { recursive: true, mode: 0o700 });
  // The marker exists only in this disposable fixture bundle; no production policy is edited.
  const fixtureRelease = { ...release, recoveryProtocol: { journalSchema: 2, ordinaryWorkIsolation: 1 } };
  write(path.join(fixtureBundle, 'release.json'), fixtureRelease);
  for (const name of ['restic', 'restic.sha256']) fs.copyFileSync(path.join(bundle, 'tools', name), path.join(fixtureBundle, 'tools', name));
  fs.chmodSync(path.join(fixtureBundle, 'tools/restic'), 0o700);
  const settings = readEnv(path.join(state, 'runtime/backup.env'));
  const configEnv = { RESTIC_REPOSITORY: process.env.RESTIC_REPOSITORY, RESTIC_PASSWORD: process.env.RESTIC_PASSWORD };
  assert.ok(path.isAbsolute(configEnv.RESTIC_REPOSITORY)); assert.ok(configEnv.RESTIC_PASSWORD.length >= 32);
  const configurationExecute = (file, args, options) => execute(file, args, { ...options, env: { ...options.env, ...configEnv } });
  const snapshot = configurationSnapshot(state, fixtureRelease);
  const configuration = runConfigurationBackup(fixtureBundle, settings, 'staging', snapshot, false, configurationExecute);
  const sql = (container, database, query) => docker(['exec', container, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'chanter_admin',
    '-d', database, '-Atc', query]).trim();
  sql(postgres, 'postgres', 'CREATE TABLE canonical_recovery_fixture_marker(id INT PRIMARY KEY); INSERT INTO canonical_recovery_fixture_marker VALUES (1)');
  docker(['exec', postgres, 'pgbackrest', '--type=full', `--annotation=release=${release.commit}`,
    `--annotation=config-snapshot=${configuration.snapshotId}`, `--annotation=inventory=${inventoryId}`,
    `--annotation=database-backup=${databaseBackupId}`, `--annotation=object-inventory=${inventoryArchive.manifest.snapshotId}`, 'backup']);
  const info = JSON.parse(docker(['exec', postgres, 'pgbackrest', '--output=json', 'info']));
  const backup = info.find(value => value.name === 'chanter').backup.at(-1);
  assert.equal(backup.annotation.inventory, inventoryId); assert.equal(backup.annotation['database-backup'], databaseBackupId);
  assert.equal(backup.annotation['object-inventory'], inventoryArchive.manifest.snapshotId);
  const targetTime = sql(postgres, 'postgres', `SELECT to_char(clock_timestamp() AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')`);
  assert.equal(new Date(targetTime).toISOString(), targetTime);
  await new Promise(resolve => setTimeout(resolve, 25));
  sql(postgres, 'postgres', 'INSERT INTO canonical_recovery_fixture_marker VALUES (2)');
  sql(postgres, 'postgres', 'SELECT pg_switch_wal()');
  docker(['exec', postgres, 'pgbackrest', 'check']);
  const journal = new JournalRepository({ bundleDir: fixtureBundle, environment: 'staging', kind: 'fixture', env: {
    RESTIC_REPOSITORY: path.join(root, 'journal-repository'), RESTIC_PASSWORD: crypto.randomBytes(32).toString('hex'),
    GOMAXPROCS: '1', GOMEMLIMIT: '256MiB' } });
  journal.initialize();
  const destination = path.join(root, 'older-database');
  const transportProject = `chanter-recovery-${crypto.randomUUID().replaceAll('-', '')}`;
  const normalClient = source => ({ ...lifecycleClient({ source, environment: 'staging', composeFile, project: transportProject,
    execute: (file, args, options) => {
      assert.equal(file, 'docker'); assert.equal(args[2], transportProject); assert.equal(args[4], composeFile);
      const translated = [...args]; translated[2] = project;
      return execute(file, translated, options);
    } }), kind: 'fixture' });
  let restored = null;
  let objectVolume = null;
  return {
    async archiveCurrent(authority) {
      const replica = await replicateJournal(normalClient('auth'), journal, normalClient('community'));
      assert.ok(sameWatermark(replica.authority, authority));
      return replica;
    },
    async recover(authority, historical, postBackupGraph, objects) {
      nonzeroUuid(historical.serverId);
      nonzeroUuid(postBackupGraph.serverId); nonzeroUuid(postBackupGraph.courseId);
      assert.ok(Array.isArray(historical.channelIds) && historical.channelIds.length > 0 && historical.channelIds.length <= 64);
      for (const id of historical.channelIds) nonzeroUuid(id);
      // No original application process may still write the source fixture database or objects.
      for (const row of docker(['ps', '--filter', `label=com.docker.compose.project=${project}`, '--format', '{{.ID}}']).trim().split('\n').filter(Boolean)) {
        const service = docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.service"}}', row]).trim();
        assert.ok(['postgres', 'redis', 'clamav'].includes(service), 'Original application writer is still running');
      }
      const runRestore = args => {
        const translated = [...args];
        if (args[0] === 'run') {
          const envIndex = translated.indexOf('--env-file'); assert.ok(envIndex > 0);
          translated[envIndex + 1] = path.join(path.dirname(sourceCompose), 'postgres-backup.env');
          assert.ok(fs.statSync(translated[envIndex + 1]).isFile());
          const imageIndex = translated.indexOf(release.images.postgres); assert.ok(imageIndex > 0);
          translated.splice(imageIndex, 0, '--volume', `${repositoryMount.Name}:/var/lib/pgbackrest/repo:ro`);
        }
        return docker(translated);
      };
      restored = await restoreIsolated({ bundleDir: fixtureBundle, settings, destination, environment: 'staging',
        label: backup.label, targetTime }, runRestore,
      (...args) => verifyConfigurationBackup(...args, configurationExecute));
      assert.equal(sql(restored.container, 'postgres', 'SELECT string_agg(id::text, chr(44) ORDER BY id) FROM canonical_recovery_fixture_marker'), '1');
      assert.equal(sql(restored.container, 'chanter_auth', 'SELECT revision FROM lifecycle_journal_head WHERE id=1'), '0');
      assert.equal(sql(restored.container, 'chanter_media', `SELECT state FROM course_resources WHERE id='${resourceId}'`), 'AVAILABLE');
      assert.equal(sql(restored.container, 'chanter_media', 'SELECT inventory_id::text || chr(58) || database_backup_id::text || chr(58) || authority_revision::text FROM media_recovery_inventory WHERE id=1'),
        `${inventoryId}:${databaseBackupId}:0`);
      assert.equal(sql(restored.container, 'chanter_community', `SELECT count(*) FROM courses WHERE id='${courseId}'`), '1');
      assert.equal(sql(restored.container, 'chanter_community', `SELECT count(*) FROM study_servers WHERE id='${postBackupGraph.serverId}'`), '0');
      assert.equal(sql(restored.container, 'chanter_community', `SELECT count(*) FROM courses WHERE id='${postBackupGraph.courseId}'`), '0');
      assert.equal(sql(restored.container, 'chanter_agent', `SELECT outcome FROM native_companion_requests WHERE id='${nativeRequestId}'`), 'ISSUED');
      assert.ok(Number(sql(restored.container, 'chanter_auth', 'SELECT count(*) FROM auth_sessions WHERE revoked_at IS NULL')) > 0);
      // POSIX WAL replay needed the read-only repository mount. Recreate only this owned, promoted fixture
      // process without that mount, matching the production operator's no-extra-mount boundary.
      assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "chanter.recovery"}}', restored.container]).trim(), restored.container);
      docker(['stop', restored.container]); docker(['rm', restored.container]);
      docker(['run', '-d', '--name', restored.container, '--label', `chanter.recovery=${restored.container}`, '--network', restored.network,
        '--pull=never', '--user', '70:70', '--read-only', '--cap-drop=ALL', '--security-opt=no-new-privileges:true',
        '--memory=896m', '--cpus=1', '--tmpfs', '/tmp:size=32m,mode=1777', '--tmpfs', '/var/run/postgresql:size=16m,mode=1777',
        '--volume', `${restored.volume}:/var/lib/postgresql/data`, '--entrypoint', 'postgres', release.images.postgres,
        '-D', '/var/lib/postgresql/data', '-c', 'archive_mode=off', '-c', 'listen_addresses=', '-c', 'shared_buffers=192MB', '-c', 'work_mem=2MB']);
      docker(['network', 'disconnect', restored.network, restored.container]);
      const recoveryOptions = { bundleDir: fixtureBundle, destination, settings,
        environment: 'staging', requiredAuthority: authority };
      let interruptMessage = true, interruptionObserved = false, invalidationCalls = 0;
      const recoveryDependencies = {
        run: (args, timeout) => {
          if (args[0] === 'compose') {
            const file = args[args.indexOf('-f') + 1];
            assert.ok(file.startsWith(destination + path.sep));
            const definition = json(file);
            // The backed-up namespace is the explicitly local fixture, never an invented S3 namespace.
            definition.services['media-service'].environment.CHANTER_MEDIA_STORAGE_BACKEND = 'local';
            write(file, definition);
          }
          return execute('docker', args, { timeout });
        },
        loadConfiguration: (...args) => readConfigurationBackup(...args, configurationExecute),
        repositoryFactory: () => journal,
        clientFactory: options => {
          const client = lifecycleClient(options);
          return { ...client, kind: 'fixture', reapply: async page => {
            // Simulate loss of one participant transport after real auth/community replay.
            // The operator must preserve the attempt and stop every owned process before retry.
            if (options.source === 'message' && interruptMessage) {
              interruptionObserved = true; throw new Error('Hosted participant interruption');
            }
            return client.reapply(page);
          }, invalidate: async request => {
            invalidationCalls++;
            const database = docker(['compose', '--project-name', options.project, '-f', options.composeFile, 'ps', '--quiet', 'postgres']).trim();
            if (options.source === 'agent') {
              assert.equal(sql(database, 'chanter_agent', `SELECT outcome || ':' || (evidence_json IS NOT NULL)::text FROM native_companion_requests WHERE id='${nativeRequestId}'`), 'ISSUED:true');
              assert.equal(sql(database, 'chanter_agent', `SELECT count(*) FROM lifecycle_terminal_targets WHERE target_id IN ('${liveGraph.serverId}','${liveOwnerId}')`), '0');
            }
            const receipt = await client.invalidate(request);
            if (options.source === 'agent') assert.equal(sql(database, 'chanter_agent', `SELECT outcome || ':' || (evidence_json IS NULL)::text FROM native_companion_requests WHERE id='${nativeRequestId}'`), 'REJECTED:true');
            return receipt;
          } };
        },
      };
      await assert.rejects(() => applyRecoveryAuthority(recoveryOptions, recoveryDependencies),
        /Current authority recovery failed/);
      assert.equal(interruptionObserved, true); assert.equal(invalidationCalls, 0);
      const attempt = path.join(destination, `authority-${checkpointIdentity('staging', authority)}`);
      const interrupted = json(path.join(attempt, 'attempt.json'));
      assert.equal(interrupted.status, 'failed-preserved'); assert.equal(interrupted.publicCutoverAllowed, false);
      assert.equal(fs.existsSync(path.join(attempt, 'authority-receipt.json')), false);
      assert.equal(docker(['ps', '--filter', `label=chanter.recovery=${restored.container}`, '--format', '{{.ID}}']).trim(), '');
      interruptMessage = false;
      const result = await applyRecoveryAuthority(recoveryOptions, recoveryDependencies);
      assert.equal(result.recoveryId, interrupted.recoveryId); assert.equal(invalidationCalls, 2);
      assert.equal(result.publicCutoverAllowed, false); assert.equal(result.isolationVerified, true);
      assert.equal(result.participants.length, 7); assert.equal(result.invalidations.length, 2);
      const recoveredCompose = path.join(attempt, 'compose.json');
      const recovered = json(recoveredCompose);
      // Operator stopped every source. Start only the restored PostgreSQL process for read-only owning-row checks.
      docker(['compose', '--project-name', recovered.name, '-f', recoveredCompose, 'up', '-d', '--no-deps', '--wait', 'postgres']);
      const database = docker(['compose', '--project-name', recovered.name, '-f', recoveredCompose, 'ps', '--quiet', 'postgres']).trim();
      assert.equal(sql(database, 'chanter_auth', 'SELECT revision FROM lifecycle_journal_head WHERE id=1'), String(authority.revision));
      assert.equal(sql(database, 'chanter_auth', 'SELECT count(*) FROM auth_sessions WHERE revoked_at IS NULL'), '0');
      assert.equal(sql(database, 'chanter_agent', `SELECT outcome || ':' || (evidence_json IS NULL)::text FROM native_companion_requests WHERE id='${nativeRequestId}'`), 'REJECTED:true');
      assert.equal(sql(database, 'chanter_community', `SELECT count(*) FROM courses WHERE id='${courseId}'`), '0');
      assert.equal(sql(database, 'chanter_community', `SELECT count(*) FROM courses WHERE id='${liveGraph.courseId}' AND study_server_id='${liveGraph.serverId}'`), '1');
      assert.equal(sql(database, 'chanter_community', `SELECT count(*) FROM study_servers WHERE id='${postBackupGraph.serverId}'`), '0');
      assert.equal(historical.courseId, courseId); assert.equal(historical.historicalFixtureOnly, true);
      for (const source of ['community', 'message', 'media', 'agent', 'notification', 'search']) {
        for (const kind of ['COURSE', 'CHANNEL']) {
          const current = objects.currentScopes.find(scope => scope.studyServerId === historical.serverId && scope.kind === kind);
          assert.ok(current && Number.isSafeInteger(current.totalCount) && current.totalCount >= 0);
          assert.match(current.scopeDigest, /^[a-f0-9]{64}$/);
          assert.equal(sql(database, `chanter_${source}`, `SELECT count(*) FROM lifecycle_scope_imports WHERE study_server_id='${historical.serverId}' AND scope_kind='${kind}' AND total_count=${current.totalCount} AND scope_digest='${current.scopeDigest}' AND ready=TRUE`), '1');
        }
        assert.equal(sql(database, `chanter_${source}`, `SELECT count(*) FROM lifecycle_scope_import_ids WHERE study_server_id='${historical.serverId}' AND scope_kind='CHANNEL' AND scope_id IN (${historical.channelIds.map(id => `'${id}'`).join(',')})`), '0');
        assert.equal(sql(database, `chanter_${source}`, `SELECT count(*) FROM lifecycle_recovery_scope_ids WHERE study_server_id='${historical.serverId}' AND scope_kind='COURSE' AND scope_id='${courseId}'`), '1');
        assert.equal(sql(database, `chanter_${source}`, `SELECT count(*) FROM lifecycle_recovery_scope_ids WHERE study_server_id='${historical.serverId}' AND scope_kind='CHANNEL' AND scope_id IN (${historical.channelIds.map(id => `'${id}'`).join(',')})`), String(historical.channelIds.length));
        assert.equal(sql(database, `chanter_${source}`, `SELECT count(*) FROM lifecycle_scope_import_ids WHERE study_server_id='${postBackupGraph.serverId}' AND scope_kind='COURSE' AND scope_id='${postBackupGraph.courseId}'`), '1');
        assert.equal(sql(database, `chanter_${source}`, `SELECT count(*) FROM lifecycle_recovery_scope_ids WHERE study_server_id='${postBackupGraph.serverId}' AND scope_kind='COURSE' AND scope_id='${postBackupGraph.courseId}'`), '1');
      }
      // Object reconstruction is a separate, stopped-source phase on this same restored database.
      // It preserves the exact private network and uses a fresh owned local namespace, never an external S3 claim.
      objectVolume = `${restored.container}-objects`;
      assert.ok(!docker(['volume', 'ls', '--format', '{{.Name}}']).split('\n').includes(objectVolume));
      docker(['volume', 'create', '--label', `chanter.recovery=${restored.container}`, objectVolume]);
      const objectCompose = structuredClone(recovered);
      objectCompose.volumes.fixture_objects = { external: true, name: objectVolume };
      const media = objectCompose.services['media-service'];
      media.tmpfs = media.tmpfs.filter(value => !value.startsWith('/app/resources:'));
      media.volumes = ['fixture_objects:/app/resources', `${root}:/opt/canonical-fixture:ro`];
      const objectFile = path.join(attempt, 'object-compose.json'); write(objectFile, objectCompose);
      const objectCall = request => JSON.parse(execute('docker', ['compose', '--project-name', recovered.name, '-f', objectFile,
        'run', '--rm', '--no-deps', '-T', '-e', 'CHANTER_SOURCE_RECOVERY_PREVIEW=true', '--entrypoint', 'java', 'media-service',
        '-cp', '/opt/canonical-fixture:/app/classes:/app/lib/*', 'ResourceRecoveryFixture'], { input: JSON.stringify(request) }));
      const fence = objectCall({ action: 'fence', inventoryId });
      assert.equal(fence.unsettledMutations, 0); assert.equal(fence.storageNamespaceSha256, objects.archivedNamespace);
      assert.deepEqual(objects.inventoryArchive, inventoryArchive);
      const archivedObjects = new Map();
      assert.equal(objects.archive.verifyInventory(inventoryArchive, objects.inventory,
        (source, object) => archivedObjects.set(`${source.resourceId}:${source.referenceKind}`, object)).referenceCount, 3);
      assert.deepEqual(archivedObjects.get(`${objects.liveObject.resourceId}:CURRENT`), objects.liveArchived);
      assert.deepEqual(archivedObjects.get(`${objects.quarantineObject.resourceId}:CURRENT`), objects.quarantineArchived);
      objectCall({ action: 'discard', inventoryId });
      const restoredInventory = objectCall({ action: 'capture', inventoryId, databaseBackupId, authority });
      assert.equal(restoredInventory.referenceCount, 3);
      const references = objectCall({ action: 'page', inventoryId, authority, after: 0, limit: 16 });
      assert.equal(references.nextAfter, null); assert.equal(references.references.length, 3);
      const live = references.references.find(value => value.resourceId === objects.liveObject.resourceId);
      const terminal = references.references.find(value => value.resourceId === resourceId);
      const quarantined = references.references.find(value => value.resourceId === objects.quarantineObject.resourceId);
      assert.equal(live.terminal, false); assert.equal(live.resourceState, 'AVAILABLE');
      assert.equal(quarantined.terminal, false); assert.equal(quarantined.resourceState, 'QUARANTINED');
      assert.equal(terminal.terminal, true); assert.equal(terminal.resourceState, 'DELETE_PENDING');
      for (const field of ['resourceId', 'courseId', 'key', 'byteSize', 'sha256', 'providerVersionId'])
        assert.equal(live[field], objects.liveObject[field]);
      for (const field of ['resourceId', 'courseId', 'key', 'byteSize', 'sha256', 'providerVersionId'])
        assert.equal(quarantined[field], objects.quarantineObject[field]);
      const bytes = objects.archive.readVerified(objects.liveArchived, objects.liveObject, fence.storageNamespaceSha256);
      assert.deepEqual(bytes, objects.actualBytes);
      const restoreRequest = { inventoryId, databaseBackupId, authority, ordinal: live.ordinal };
      const terminalRequest = { inventoryId, databaseBackupId, authority, ordinal: terminal.ordinal };
      assert.deepEqual(objectCall({ action: 'expect-refusal', request: terminalRequest, reason: 'STALE_SOURCE', base64: bytes.toString('base64') }),
        { refused: 'STALE_SOURCE', publicCutoverAllowed: false });
      assert.equal(objectCall({ action: 'fence', inventoryId }).unsettledMutations, 0);
      const quarantineBytes = objects.archive.readVerified(objects.quarantineArchived, objects.quarantineObject, fence.storageNamespaceSha256);
      assert.deepEqual(quarantineBytes, objects.quarantineBytes);
      assert.equal(quarantineBytes.length,10*1024*1024);
      const quarantineRequest = { inventoryId, databaseBackupId, authority, ordinal: quarantined.ordinal };
      const httpRestoration=withResourceRecoveryHttp({definition:objectCompose,root:attempt,project:recovered.name},client=>{
        assert.throws(()=>client.put(terminalRequest,bytes));
        assert.equal(client.fence(inventoryId).unsettledMutations,0);
        client.put(restoreRequest,bytes);
        client.put(quarantineRequest,quarantineBytes);
        assert.deepEqual(client.read(restoreRequest),bytes);
        assert.deepEqual(client.read(quarantineRequest),quarantineBytes);
        assert.throws(()=>client.put(quarantineRequest,quarantineBytes));
        assert.equal(client.fence(inventoryId).unsettledMutations,0);
        client.delete(terminalRequest);
        const finish={inventoryId,databaseBackupId,authority,resourceId};
        assert.equal(client.finish(finish).sourceAccountingCommitted,true);
        assert.equal(client.finish(finish).sourceAccountingCommitted,true);
        assert.deepEqual(client.read(quarantineRequest),quarantineBytes);
        return {maximumQuarantineBytes:quarantineBytes.length,actualSourceCompletion:true};
      });
      assert.equal(sql(database, 'chanter_media', `SELECT state FROM course_resources WHERE id='${quarantined.resourceId}'`), 'QUARANTINED');
      assert.equal(sql(database,'chanter_media',`SELECT state || ':' || byte_reservation::text FROM course_resources WHERE id='${resourceId}'`),'DELETED:false');
      assert.equal(sql(database,'chanter_media','SELECT reserved_bytes FROM media_storage_budget WHERE id=1'),String(live.byteSize+quarantined.byteSize));
      const mediaReceipt = objectCall({ action: 'receipt' });
      assert.equal(mediaReceipt.source, 'media'); assert.equal(mediaReceipt.schemaVersion, 1);
      assert.ok(sameWatermark(mediaReceipt.authority, authority));
      assert.equal(docker(['ps', '--filter', `volume=${objectVolume}`, '--format', '{{.ID}}']).trim(), '');
      return { ...result, databaseBackupVerified: true, restoredSessionsInvalidated: true,
        restoredPendingNativeInvalidated: true, historicalCourseReconciled: true, objectRestoreVerified: true,
        postBackupServerReconciled: true,
        quarantineStatePreserved: true,httpRestoration,
        interruptedParticipantRetryVerified: true,
        mediaReceiptAfterObjectClosure: mediaReceipt, terminalObjectRestorationRefused: true,
        externalProviderClosureVerified: false };
    },
    cleanup() {
      // Exact CI-owned identities only. All removals are attempted; no production cleanup path is changed.
      const failures = [];
      if (!restored && fs.existsSync(path.join(destination, 'recovery.json'))) restored = json(path.join(destination, 'recovery.json'));
      if (!restored) return;
      const id = restored.container;
      for (const container of docker(['ps', '-a', '--filter', `label=chanter.recovery=${id}`, '--format', '{{.ID}}']).trim().split('\n').filter(Boolean)) {
        try { assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "chanter.recovery"}}', container]).trim(), id);
          docker(['stop', container]); docker(['rm', container]); } catch { failures.push('container'); }
      }
      for (const network of [restored.network, `${id}-authority`]) try {
        if (!docker(['network', 'ls', '--format', '{{.Name}}']).split('\n').includes(network)) continue;
        assert.equal(docker(['network', 'inspect', '--format', '{{index .Labels "chanter.recovery"}}', network]).trim(), id);
        docker(['network', 'rm', network]);
      } catch { failures.push('network'); }
      try { assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "chanter.recovery"}}', restored.volume]).trim(), id);
        docker(['volume', 'rm', restored.volume]); } catch { failures.push('volume'); }
      if (objectVolume) try {
        assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "chanter.recovery"}}', objectVolume]).trim(), id);
        docker(['volume', 'rm', objectVolume]);
      } catch { failures.push('object volume'); }
      assert.deepEqual(failures, [], 'Owned database fixture cleanup failed');
    },
  };
}
