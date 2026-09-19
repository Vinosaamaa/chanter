import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import http from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { publishAssets, githubReleaseIo } from './publish-assets.mjs';

const scratch = path.resolve('.cache/deploy-tests');
fs.mkdirSync(scratch, { recursive: true });

test('the actual GitHub CLI streams a binary upload with its exact content length', async t => {
  const run = promisify(execFile);
  const executable = process.env.GH_EXECUTABLE || 'gh';
  try { await run(executable, ['--version']); }
  catch (error) { if (error.code === 'ENOENT') return t.skip('GitHub CLI is required in the release workflow'); throw error; }
  const root = fs.mkdtempSync(path.join(scratch, 'upload-transport-'));
  const file = path.join(root, 'fixture.bin');
  const bytes = crypto.randomBytes(4096);
  fs.writeFileSync(file, bytes);
  let received;
  const server = http.createServer(async (request, response) => {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    received = { bytes: Buffer.concat(chunks), length: request.headers['content-length'], method: request.method };
    response.writeHead(201, { 'Content-Type': 'application/json' });
    response.end('{}');
  });
  t.after(async () => {
    await new Promise(resolve => server.close(resolve));
    assert.ok(path.resolve(root).startsWith(scratch + path.sep));
    fs.rmSync(root, { recursive: true });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  await run(executable, ['api', `http://127.0.0.1:${server.address().port}/upload`, '--method', 'POST', '--input', file,
    '--header', 'Content-Type: application/octet-stream', '--header', `Content-Length: ${bytes.length}`, '--silent'],
  { env: { ...process.env, GH_TOKEN: 'synthetic-fixture-token', GH_DEBUG: '' } });
  assert.deepEqual(received, { bytes, length: String(bytes.length), method: 'POST' });
});

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

test('upload stays bound to the validated ID and rejects a draft published between files', () => {
  const tag = 'deploy-' + 'a'.repeat(40);
  const release = { id: 123, tag_name: tag, draft: true };
  let current = release;
  const calls = [];
  const io = githubReleaseIo('example/chanter', (_command, args) => {
    calls.push(args);
    if (args[1] === 'repos/example/chanter/releases/123') return JSON.stringify(current);
    return '{}';
  });
  const file = path.resolve('scripts/deploy/publish-assets.test.mjs');
  io.upload(release, file);
  assert.equal(calls.length, 2);
  assert.equal(calls[1][1], 'https://uploads.github.com/repos/example/chanter/releases/123/assets?name=publish-assets.test.mjs');
  assert.ok(calls[1].includes('--input'));
  current = { ...release, draft: false };
  assert.throws(() => io.upload(release, file), /draft/);
  assert.equal(calls.length, 3);
  current = { ...release, tag_name: 'another-tag' };
  assert.throws(() => io.upload(release, file), /identity/);
  assert.equal(calls.length, 4);
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
    upload: (release, file) => uploads.push([release.draft, path.basename(file)]) };
  await publishAssets('deploy-fixture', files, io);
  assert.deepEqual(uploads, [[true, 'bundle.tar.gz.sha256']]);

  uploads.length = 0;
  io.readRelease = () => ({ draft: true, assets: [{ name: 'bundle.tar.gz', digest: 'sha256:' + '0'.repeat(64) }] });
  await assert.rejects(publishAssets('deploy-fixture', files, io), /differs|digest/);
  assert.deepEqual(uploads, []);

  io.readRelease = () => ({ draft: false, assets: [{ name: 'bundle.tar.gz', digest }] });
  await assert.rejects(publishAssets('deploy-fixture', files, io), /published/);
  assert.deepEqual(uploads, []);
});
