import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { publishAssets } from './publish-assets.mjs';

const scratch = path.resolve('.cache/deploy-tests');
fs.mkdirSync(scratch, { recursive: true });

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
