import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

// Serialize each commit/architecture in the workflow. Never clobber a release
// asset: retry only the missing files after verifying GitHub's stored digests.
export async function publishAssets(tag, files, io) {
  const release = await io.readRelease(tag);
  const pending = [];
  for (const file of files) {
    const hash = crypto.createHash('sha256');
    for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
    const name = path.basename(file);
    const existing = release.assets.find(asset => asset.name === name);
    if (existing) {
      if (existing.digest !== `sha256:${hash.digest('hex')}`) {
        throw new Error(`Existing release asset differs or lacks a verified digest: ${name}; preserved unchanged`);
      }
    } else pending.push(file);
  }
  if (pending.length && !release.draft) throw new Error('Cannot add assets to an already published release');
  for (const file of pending) await io.upload(tag, file);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const [tag, ...files] = process.argv.slice(2);
  const repo = process.env.GITHUB_REPOSITORY;
  if (!/^deploy-[a-f0-9]{40}$/.test(tag ?? '') || !files.length || !/^[\w.-]+\/[\w.-]+$/.test(repo ?? '')) {
    throw new Error('A commit deployment tag, asset paths and GITHUB_REPOSITORY are required');
  }
  await publishAssets(tag, files, {
    readRelease: name => JSON.parse(execFileSync('gh', ['api', `repos/${repo}/releases/tags/${name}`], { encoding: 'utf8' })),
    upload: (name, file) => execFileSync('gh', ['release', 'upload', name, file, '--repo', repo], { stdio: 'inherit' }),
  });
}
