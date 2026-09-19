import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { publishAssets, githubReleaseIo } from './publish-assets.mjs';

const scratch = path.resolve('.cache/deploy-tests');
fs.mkdirSync(scratch, { recursive: true });

test('draft discovery uses the release ID and includes assets beyond the first page', () => {
  const calls = [];
  const tag = 'deploy-' + 'a'.repeat(40);
  const io = githubReleaseIo('example/chanter', (command, args) => {
    calls.push([command, ...args]);
    if (args[0] === 'release') return JSON.stringify({ databaseId: 123 });
    if (args[1] === 'repos/example/chanter/releases/123') return JSON.stringify({ id: 123, tag_name: tag, draft: true });
    if (args[1] === 'repos/example/chanter/releases/123/assets') return JSON.stringify([[{ name: 'first' }], [{ name: 'second' }]]);
    throw new Error('Unexpected GitHub request');
  });
  assert.deepEqual(io.readRelease(tag), { id: 123, tag_name: tag, draft: true, assets: [{ name: 'first' }, { name: 'second' }] });
  assert.deepEqual(calls, [
    ['gh', 'release', 'view', tag, '--repo', 'example/chanter', '--json', 'databaseId'],
    ['gh', 'api', 'repos/example/chanter/releases/123'],
    ['gh', 'api', 'repos/example/chanter/releases/123/assets', '--paginate', '--slurp'],
  ]);
});

test('release discovery fails closed when its returned identity changes', () => {
  const tag = 'deploy-' + 'a'.repeat(40);
  for (const identity of [{ id: 123, tag_name: 'another-tag' }, { id: 124, tag_name: tag }]) {
    const io = githubReleaseIo('example/chanter', (_command, args) => JSON.stringify(args[0] === 'release' ? { databaseId: 123 } : identity));
    assert.throws(() => io.readRelease(tag), /identity/);
  }
});

test('a partial release retry preserves identical uploaded assets and uploads only missing files', async t => {
  const root = fs.mkdtempSync(path.join(scratch, 'assets-'));
  t.after(() => {
    assert.ok(path.resolve(root).startsWith(scratch + path.sep));
    fs.rmSync(root, { recursive: true });
  });
  const files = ['bundle.tar.gz', 'bundle.tar.gz.sha256'].map(name => path.join(root, name));
  files.forEach(file => fs.writeFileSync(file, 'synthetic release bytes'));
  const digest = 'sha256:' + crypto.createHash('sha256').update('synthetic release bytes').digest('hex');
  const uploads = [];
  const io = { readRelease: () => ({ draft: true, assets: [{ name: 'bundle.tar.gz', digest }] }),
    upload: (tag, file) => uploads.push([tag, path.basename(file)]) };
  await publishAssets('deploy-fixture', files, io);
  assert.deepEqual(uploads, [['deploy-fixture', 'bundle.tar.gz.sha256']]);

  uploads.length = 0;
  io.readRelease = () => ({ draft: true, assets: [{ name: 'bundle.tar.gz', digest: 'sha256:' + '0'.repeat(64) }] });
  await assert.rejects(publishAssets('deploy-fixture', files, io), /differs|digest/);
  assert.deepEqual(uploads, []);

  io.readRelease = () => ({ draft: false, assets: [{ name: 'bundle.tar.gz', digest }] });
  await assert.rejects(publishAssets('deploy-fixture', files, io), /published/);
  assert.deepEqual(uploads, []);
});
