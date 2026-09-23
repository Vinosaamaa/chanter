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
    deferredJs: [{ name: 'Optional', entries: ['optional'], rawBytes: 300, gzipBytes: 1000 }] }
  const save = () => writeFile(path.join(root, '.vite/manifest.json'), JSON.stringify(manifest))
  try {
    await save()
    const measured = await enforceBundleBudget(root, budget)
    assert.equal(measured.javascript.rawBytes, 200)
    assert.equal(measured.deferredJs[0].rawBytes, 300)
    await assert.rejects(enforceBundleBudget(root, { ...budget, jsRawBytes: 199 }), /raw bundle/)
    await assert.rejects(enforceBundleBudget(root, { ...budget, deferredJs: [{ ...budget.deferredJs[0], rawBytes: 299 }] }), /deferred JavaScript/)
    await writeFile(path.join(root, 'assets/second.js'), 'y'.repeat(200))
    manifest.second = { file: 'assets/second.js', isDynamicEntry: true, imports: ['shared'] }
    const combined = { ...budget, deferredJs: [...budget.deferredJs,
      { name: 'Second', entries: ['second'], rawBytes: 200, gzipBytes: 1000 }] }
    await save()
    assert.equal((await enforceBundleBudget(root, combined)).javascript.rawBytes, 200)
    await assert.rejects(enforceBundleBudget(root, { ...combined, deferredJs: [...budget.deferredJs,
      { ...combined.deferredJs[1], rawBytes: 199 }] }), /Second deferred JavaScript/)
    await assert.rejects(enforceBundleBudget(root, { ...combined, deferredJs: [...combined.deferredJs,
      { ...budget.deferredJs[0], name: 'Duplicate' }] }), /Duplicate deferred JavaScript allowance/)
    manifest['index.html'].imports = ['optional']
    await save()
    await assert.rejects(enforceBundleBudget(root, combined), /included in initial entry/)
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
    assert.equal(result.deferredCss[0].rawBytes, 40)
    await writeFile(path.join(root, 'assets/account.css'), 'c'.repeat(30))
    manifest.account = { file: 'assets/route.js', css: ['assets/account.css'] }
    await writeFile(path.join(root, '.vite/manifest.json'), JSON.stringify(manifest))
    const combined = { ...budget, deferredCss: [budget.deferredCss,
      { name: 'Account data', entries: ['account'], rawBytes: 30, gzipBytes: 1000 }] }
    assert.equal((await enforceBundleBudget(root, combined)).css.rawBytes, 80)
    await assert.rejects(enforceBundleBudget(root, { ...combined, cssRawBytes: 79 }), /CSS raw bundle/)
    await assert.rejects(enforceBundleBudget(root, { ...combined, deferredCss: [...combined.deferredCss,
      { ...budget.deferredCss, name: 'Duplicate' }] }), /Duplicate deferred CSS allowance/)
    await assert.rejects(enforceBundleBudget(root, { ...combined, deferredCss: [budget.deferredCss,
      { ...combined.deferredCss[1], rawBytes: 29 }] }), /Account data deferred CSS/)
    manifest['index.html'].css.push('assets/moderation.css')
    await writeFile(path.join(root, '.vite/manifest.json'), JSON.stringify(manifest))
    await assert.rejects(enforceBundleBudget(root, combined), /Moderation CSS is included in Home/)
  } finally { await rm(root, { recursive: true, force: true }) }
})

test('only exact new JavaScript entries receive a deferred allowance and shared code stays in core', async () => {
  const root=await mkdtemp(path.join(tmpdir(),'chanter-deferred-js-'))
  await mkdir(path.join(root,'assets')); await mkdir(path.join(root,'.vite'))
  for(const [name,size] of [['entry',40],['shared',80],['safety',50]]) await writeFile(path.join(root,`assets/${name}.js`),'x'.repeat(size))
  const manifest={'index.html':{file:'assets/entry.js'},shared:{file:'assets/shared.js'},safety:{file:'assets/safety.js',isDynamicEntry:true,imports:['shared']}}
  const budget={jsRawBytes:125,jsGzipBytes:1000,cssRawBytes:1000,cssGzipBytes:1000,
    deferredJs:[{name:'Moderation',entries:['safety'],rawBytes:60,gzipBytes:1000}],routes:[{name:'Home',entries:[]}]}
  try {
    await writeFile(path.join(root,'.vite/manifest.json'),JSON.stringify(manifest))
    const result=await enforceBundleBudget(root,budget)
    assert.equal(result.javascript.rawBytes,120)
    assert.equal(result.deferredJs[0].rawBytes,50)
    await assert.rejects(enforceBundleBudget(root,{...budget,jsRawBytes:100}),/JavaScript raw bundle is 120 bytes/)
    manifest['index.html'].imports=['safety']
    await writeFile(path.join(root,'.vite/manifest.json'),JSON.stringify(manifest))
    await assert.rejects(enforceBundleBudget(root,budget),/Moderation JavaScript is included in Home/)
  } finally { await rm(root,{recursive:true,force:true}) }
})
