import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { enforceBundleBudget, measureImportGraph } from './check-bundle-budget.mjs'

test('optional SDK cap cannot hide shared code or become a startup dependency', async () => {
  const scratch = path.resolve('.cache/bundle-tests')
  await mkdir(scratch, { recursive: true })
  const root = await mkdtemp(path.join(scratch, 'optional-'))
  await mkdir(path.join(root, 'assets'))
  await mkdir(path.join(root, '.vite'))
  for (const [name, size] of [['entry', 100], ['shared', 100], ['optional', 300]]) {
    await writeFile(path.join(root, 'assets', `${name}.js`), 'x'.repeat(size))
  }
  const manifest = {
    'index.html': { file: 'assets/entry.js', dynamicImports: ['optional'] },
    shared: { file: 'assets/shared.js' },
    optional: { file: 'assets/optional.js', imports: ['shared'], isDynamicEntry: true },
  }
  const budget = { jsRawBytes: 200, jsGzipBytes: 1000, cssRawBytes: 0, cssGzipBytes: 0,
    deferredJs: { name: 'Optional', entries: ['optional'], rawBytes: 300, gzipBytes: 1000 } }
  const save = () => writeFile(path.join(root, '.vite/manifest.json'), JSON.stringify(manifest))
  try {
    await save()
    const measured = await enforceBundleBudget(root, budget)
    assert.equal(measured.javascript.rawBytes, 200)
    assert.equal(measured.deferredJs.rawBytes, 300)
    await assert.rejects(enforceBundleBudget(root, { ...budget, jsRawBytes: 199 }), /raw bundle/)
    await assert.rejects(enforceBundleBudget(root, { ...budget, deferredJs: { ...budget.deferredJs, rawBytes: 299 } }), /deferred JavaScript/)
    manifest['index.html'].imports = ['optional']
    await save()
    await assert.rejects(enforceBundleBudget(root, budget), /included in initial entry/)
  } finally {
    assert.ok(root.startsWith(scratch + path.sep))
    await rm(root, { recursive: true })
  }
})

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
