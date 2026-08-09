import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { enforceBundleBudget } from './check-bundle-budget.mjs'

test('production bundle budget rejects an oversized JavaScript asset', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'chanter-bundle-budget-'))
  const assets = path.join(root, 'assets')
  await mkdir(assets)
  await writeFile(path.join(assets, 'index.js'), 'x'.repeat(101))

  try {
    await assert.rejects(
      enforceBundleBudget(root, { jsRawBytes: 100, jsGzipBytes: 1000, cssRawBytes: 1000, cssGzipBytes: 1000 }),
      /JavaScript raw bundle is 101 bytes; budget is 100 bytes/,
    )
  } finally {
    await rm(root, { recursive: true, force: true })
  }
})
