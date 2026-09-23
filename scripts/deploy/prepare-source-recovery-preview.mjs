/** Disposable hosted dependency union. Never an accepted release or a local worktree mutation. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

assert.equal(process.env.GITHUB_ACTIONS, 'true');
assert.equal(process.env.CHANTER_SOURCE_RECOVERY_PREVIEW, 'true');
const source = 'ec91670e44373f69a1f15a010e6529103862e7df';
const frontend = '435a40b2d40a39512973dd3be139e7f1bc7042d2';
const git = args => execFileSync('git', args, { encoding: 'utf8', timeout: 60_000 }).trim();
const own = git(['rev-parse', 'HEAD']);
assert.equal(own, process.env.GITHUB_SHA);
assert.match(own, /^[a-f0-9]{40}$/);
assert.equal(git(['status', '--porcelain']), '');
git(['fetch', '--no-tags', 'origin', frontend]);
assert.equal(git(['rev-parse', 'FETCH_HEAD']), frontend);
assert.deepEqual(git(['diff', '--name-only', own, frontend, '--', 'infra', 'scripts/deploy/release.mjs']).split('\n'),
  ['infra/production/java/Dockerfile', 'infra/production/java/Lifecycle.java'],
  'Only the reviewed owning-header helper correction may differ from the pinned UI infrastructure');
git(['fetch', '--no-tags', 'origin', source]);
assert.equal(git(['rev-parse', 'FETCH_HEAD']), source);
const prefix = 'backend/media-service/src/';
const conflicts = [
  'main/java/com/chanter/media/application/PrivateResourceStorage.java',
  'main/java/com/chanter/media/infra/LocalPrivateResourceStorage.java',
  'main/java/com/chanter/media/infra/S3PrivateResourceStorage.java',
  'test/java/com/chanter/media/application/LocalPrivateResourceStorageTest.java',
  'test/java/com/chanter/media/application/S3AdapterPolicyTest.java',
].map(file => prefix + file).sort();
try { git(['-c', 'user.name=Chanter hosted recovery fixture', '-c', 'user.email=fixture@example.test',
  'merge', '--no-commit', '--no-ff', source]); } catch { /* Exact conflict set is checked below. */ }
assert.equal(git(['rev-parse', 'MERGE_HEAD']), source);
assert.deepEqual(git(['diff', '--name-only', '--diff-filter=U']).split('\n').sort(), conflicts);
for (const file of conflicts) git(['checkout', '--ours', '--', file]);

// Preserve #251's definitive rejection regression as well as #342's accounting tests.
const policy = prefix + 'test/java/com/chanter/media/application/S3AdapterPolicyTest.java';
let policyText = git(['show', `${source}:${policy}`]);
policyText = policyText.replaceAll('new S3PrivateResourceStorage(lifecycle,',
  'new S3PrivateResourceStorage(lifecycle,mock(StorageMutationStore.class),');
assert.equal((policyText.match(/mock\(StorageMutationStore.class\)/g) ?? []).length, 3);
fs.writeFileSync(policy, policyText + '\n');

// These are the three source-owner-approved hooks, against the exact pinned source.
const lifecycle = prefix + 'main/java/com/chanter/media/application/ResourceLifecycle.java';
let code = fs.readFileSync(lifecycle, 'utf8');
function replace(before, after) {
  assert.equal(code.split(before).length, 2, 'Source maintenance hook changed');
  code = code.replace(before, after);
}
replace('private final boolean recovery;', 'private final boolean recovery;\n    private final StorageMutationStore mutations;');
replace('public ResourceLifecycle(JdbcClient jdbc, Clock clock,', 'public ResourceLifecycle(JdbcClient jdbc, Clock clock, StorageMutationStore mutations,');
replace('this.recovery=recovery;', 'this.recovery=recovery; this.mutations=mutations;');
replace('lockTerminal(); requireWritable(r);', 'lockTerminal(); requireWritable(r);\n        if (!mutations.ordinaryWorkAllowed()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Storage maintenance is active");');
replace('public Optional<Job> claim(boolean migrateLegacy) {\n        lockTerminal();',
  'public Optional<Job> claim(boolean migrateLegacy) {\n        lockTerminal();\n        if (!mutations.ordinaryWorkAllowed()) return Optional.empty();');
replace('public boolean beginMigrationWrite(UUID id,UUID lease) {',
  'public boolean beginMigrationWrite(UUID id,UUID lease) {\n        lockTerminal();\n        if (!mutations.ordinaryWorkAllowed()) return false;');
fs.writeFileSync(lifecycle, code);
git(['add', '--', ...conflicts, lifecycle]);
assert.equal(git(['diff', '--name-only', '--diff-filter=U']), '');
// Use the complete owning lazy-route UI tree, never just its budget checker.
git(['restore', '--source', frontend, '--staged', '--worktree', '--', 'frontend']);
assert.equal(git(['diff', '--cached', '--name-only', frontend, '--', 'frontend']), '');
git(['-c', 'user.name=Chanter hosted recovery fixture', '-c', 'user.email=fixture@example.test',
  'commit', '--no-verify', '-m', `Disposable recovery preview ${own} + ${source} + UI ${frontend}`]);
fs.mkdirSync('.cache', { recursive: true });
fs.writeFileSync('.cache/source-recovery-preview.json', JSON.stringify({
  schemaVersion: 1, source, recovery: own, frontend, preview: git(['rev-parse', 'HEAD']), accepted: false, publicCutoverAllowed: false,
}) + '\n');
console.log(`Disposable source preview ${source} + recovery ${own} + UI ${frontend}; not accepted and capability remains OFF.`);
