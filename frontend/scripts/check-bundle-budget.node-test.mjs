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

test('deferred feature CSS has its own cap and cannot enter a protected initial route', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'chanter-deferred-css-'))
  await mkdir(path.join(root, 'assets'))
  await mkdir(path.join(root, '.vite'))
  await writeFile(path.join(root, 'assets/core.css'), 'a'.repeat(80))
  await writeFile(path.join(root, 'assets/moderation.css'), 'b'.repeat(40))
  await writeFile(path.join(root, 'assets/entry.js'), 'entry')
  await writeFile(path.join(root, 'assets/route.js'), 'route')
  const manifest = { 'index.html': { file: 'assets/entry.js', css: ['assets/core.css'] }, safety: { file: 'assets/route.js', css: ['assets/moderation.css'] } }
  const budget = { jsRawBytes: 1000, jsGzipBytes: 1000, cssRawBytes: 100, cssGzipBytes: 1000,
    deferredCss: { name: 'Moderation', entries: ['safety'], rawBytes: 50, gzipBytes: 1000 },
    routes: [{ name: 'Home', entries: [] }] }
  try {
    await writeFile(path.join(root, '.vite/manifest.json'), JSON.stringify(manifest))
    const result = await enforceBundleBudget(root, budget)
    assert.equal(result.css.rawBytes, 80)
    assert.equal(result.deferredCss.rawBytes, 40)
    manifest['index.html'].css.push('assets/moderation.css')
    await writeFile(path.join(root, '.vite/manifest.json'), JSON.stringify(manifest))
    await assert.rejects(enforceBundleBudget(root, budget), /Moderation CSS is included in Home/)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('only exact new JavaScript entries receive a deferred allowance and shared code stays in core', async () => {
  const root=await mkdtemp(path.join(tmpdir(),'chanter-deferred-js-'))
  await mkdir(path.join(root,'assets')); await mkdir(path.join(root,'.vite'))
  for(const [name,size] of [['entry',40],['shared',80],['safety',50]]) await writeFile(path.join(root,`assets/${name}.js`),'x'.repeat(size))
  const manifest={'index.html':{file:'assets/entry.js'},shared:{file:'assets/shared.js'},safety:{file:'assets/safety.js',isDynamicEntry:true,imports:['shared']}}
  const budget={jsRawBytes:125,jsGzipBytes:1000,cssRawBytes:1000,cssGzipBytes:1000,
    deferredJs:{name:'Moderation',entries:['safety'],rawBytes:60,gzipBytes:1000},routes:[{name:'Home',entries:[]}]}
  try {
    await writeFile(path.join(root,'.vite/manifest.json'),JSON.stringify(manifest))
    const result=await enforceBundleBudget(root,budget)
    assert.equal(result.javascript.rawBytes,120)
    assert.equal(result.deferredJs.rawBytes,50)
    await assert.rejects(enforceBundleBudget(root,{...budget,jsRawBytes:100}),/JavaScript raw bundle is 120 bytes/)
    manifest['index.html'].imports=['safety']
    await writeFile(path.join(root,'.vite/manifest.json'),JSON.stringify(manifest))
    await assert.rejects(enforceBundleBudget(root,budget),/Moderation JavaScript is included in Home/)
  } finally { await rm(root,{recursive:true,force:true}) }
})
