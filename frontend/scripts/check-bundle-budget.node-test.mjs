import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { enforceBundleBudget, measureImportGraph } from './check-bundle-budget.mjs'

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


test('initial transfer counts shared static imports once and excludes deferred imports', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'chanter-bundle-graph-'))
  await mkdir(path.join(root, 'assets'))
  for (const [name, size] of [['entry', 100], ['shared', 200], ['route', 300], ['voice', 500]]) {
    await writeFile(path.join(root, 'assets', `${name}.js`), name.repeat(size))
  }
  const manifest = {
    'index.html': { file: 'assets/entry.js', imports: ['shared'], dynamicImports: ['voice'] },
    shared: { file: 'assets/shared.js' },
    route: { file: 'assets/route.js', imports: ['shared'] },
    voice: { file: 'assets/voice.js' },
  }
  try {
    const initial = await measureImportGraph(root, manifest, ['index.html'])
    assert.equal(initial.rawBytes, 500 + 1200)
    const withRoute = await measureImportGraph(root, manifest, ['index.html', 'route'])
    assert.equal(withRoute.rawBytes, 500 + 1200 + 1500)
    assert.equal(withRoute.files.length, 3)
    await assert.rejects(measureImportGraph(root, manifest, ['missing']), /Missing manifest entry/)
  } finally {
    await rm(root, { recursive: true, force: true })
  }
})
