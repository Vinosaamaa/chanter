import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

export const productionBundleBudget = Object.freeze({
  jsRawBytes: 1_300_000,
  jsGzipBytes: 400_000,
  cssRawBytes: 220_000,
  cssGzipBytes: 45_000,
  deferredCss: [{
    name: 'Moderation', rawBytes: 6_000, gzipBytes: 2_000,
    entries: ['src/features/moderation/SafetyPage.tsx', 'src/features/moderation/OperatorPage.tsx', 'src/features/moderation/AppealPage.tsx'],
  }, {
    name: 'Account data', rawBytes: 3_000, gzipBytes: 1_000,
    entries: ['src/features/account-data/AccountDataPage.tsx'],
  }, {
    name: 'Account deletion', rawBytes: 3_500, gzipBytes: 1_200,
    entries: ['src/features/account-data/AccountDeletionPage.tsx', 'src/features/account-data/SourceDeletionPage.tsx'],
  }],
  deferredJs: [{
    name: 'Moderation', rawBytes: 60_000, gzipBytes: 20_000,
    entries: ['src/features/moderation/SafetyPage.tsx', 'src/features/moderation/OperatorPage.tsx', 'src/features/moderation/AppealPage.tsx'],
  }, {
    name: 'Browser errors', rawBytes: 70_000, gzipBytes: 25_000,
    entries: ['src/lib/browser-errors-client.ts'],
  }, {
    name: 'Account data', rawBytes: 16_000, gzipBytes: 6_000,
    entries: ['src/features/account-data/AccountDataPage.tsx'],
  }, {
    name: 'Account deletion', rawBytes: 14_000, gzipBytes: 5_000,
    entries: ['src/features/account-data/AccountDeletionPage.tsx'],
  }, {
    name: 'Source deletion', rawBytes: 8_000, gzipBytes: 3_000,
    entries: ['src/features/account-data/SourceDeletionPage.tsx'],
  }, {
    name: 'Privacy information', rawBytes: 4_500, gzipBytes: 2_000,
    entries: ['src/features/auth/pages/PrivacyPage.tsx'],
  }],
  initialJsGzipBytes: 120_000,
  deferredChunkGzipBytes: 130_000,
  routes: [
    { name: 'public landing', entries: ['src/features/marketing/pages/LandingPage.tsx'], gzipBytes: 140_000 },
    { name: 'sign in', entries: ['src/features/auth/pages/SignInPage.tsx'], gzipBytes: 140_000 },
    { name: 'signed-in Home', entries: ['src/features/v2-shell/layouts/V2AppShellLayout.tsx', 'src/features/v2-shell/pages/HomePage.tsx'], gzipBytes: 150_000 },
  ],
})

async function assetFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const entryPath = path.join(directory, entry.name)
    return entry.isDirectory() ? assetFiles(entryPath) : [entryPath]
  }))
  return nested.flat()
}

async function measure(files) {
  const contents = await Promise.all(files.map((file) => readFile(file)))
  return {
    rawBytes: contents.reduce((total, content) => total + content.byteLength, 0),
    gzipBytes: contents.reduce((total, content) => total + gzipSync(content).byteLength, 0),
  }
}

/** Follow static imports only; dynamic routes are measured when they are actually requested. */
export async function measureImportGraph(distDirectory, manifest, entryKeys, kind = 'js') {
  const visited = new Set()
  const files = new Set()
  const visit = key => {
    if (visited.has(key)) return
    visited.add(key)
    const entry = manifest[key]
    if (!entry) throw new Error(`Missing manifest entry: ${key}`)
    if (entry.file.endsWith(`.${kind}`)) files.add(entry.file)
    if (kind === 'css') for (const css of entry.css ?? []) files.add(css)
    for (const dependency of entry.imports ?? []) visit(dependency)
  }
  for (const key of entryKeys) visit(key)
  return { ...await measure([...files].map(file => path.join(distDirectory, file))), files: [...files] }
}

export async function enforceBundleBudget(distDirectory, budget = productionBundleBudget) {
  const files = await assetFiles(distDirectory)
  const failures = []
  const deferredJs = []
  const deferredJsFiles = new Set()
  for (const allowance of budget.deferredJs ?? []) {
    const manifest = JSON.parse(await readFile(path.join(distDirectory, '.vite/manifest.json'), 'utf8'))
    const ownedFiles = [...new Set(allowance.entries.map(key => {
      if (!manifest[key]?.isDynamicEntry) throw new Error(`Expected a new deferred entry: ${key}`)
      return manifest[key].file
    }))]
    const measured = { name: allowance.name, ...await measure(ownedFiles.map(file => path.join(distDirectory, file))), files: ownedFiles }
    deferredJs.push(measured)
    for (const file of ownedFiles) {
      const resolved = path.resolve(distDirectory, file)
      if (deferredJsFiles.has(resolved)) throw new Error(`Duplicate deferred JavaScript allowance: ${file}`)
      deferredJsFiles.add(resolved)
    }
    if (measured.rawBytes > allowance.rawBytes || measured.gzipBytes > allowance.gzipBytes)
      failures.push(`${allowance.name} deferred JavaScript exceeds its ${allowance.rawBytes} raw / ${allowance.gzipBytes} gzip byte cap`)
    for (const route of [{ name: 'initial entry', entries: [] }, ...budget.routes ?? []]) {
      const initialJs = await measureImportGraph(distDirectory, manifest, ['index.html', ...route.entries])
      if (initialJs.files.some(file => ownedFiles.includes(file))) failures.push(`${allowance.name} JavaScript is included in ${route.name}`)
    }
  }
  // Only the exact new entry chunks are separate. Shared/vendor imports remain in the core budget.
  const javascript = await measure(files.filter(file => file.endsWith('.js') && !deferredJsFiles.has(path.resolve(file))))
  const deferredCss = []
  const deferredCssFiles = new Set()
  for (const allowance of Array.isArray(budget.deferredCss) ? budget.deferredCss : budget.deferredCss ? [budget.deferredCss] : []) {
    const manifest = JSON.parse(await readFile(path.join(distDirectory, '.vite/manifest.json'), 'utf8'))
    const ownedFiles = [...new Set(allowance.entries.flatMap(key => {
      if (!manifest[key]) throw new Error(`Missing manifest entry: ${key}`)
      return manifest[key].css ?? []
    }))]
    const measured = { name: allowance.name, ...await measure(ownedFiles.map(file => path.join(distDirectory, file))), files: ownedFiles }
    deferredCss.push(measured)
    for (const file of ownedFiles) {
      const resolved = path.resolve(distDirectory, file)
      if (deferredCssFiles.has(resolved)) throw new Error(`Duplicate deferred CSS allowance: ${file}`)
      deferredCssFiles.add(resolved)
    }
    if (!ownedFiles.length) failures.push(`${allowance.name} CSS must remain a measured route dependency`)
    if (measured.rawBytes > allowance.rawBytes || measured.gzipBytes > allowance.gzipBytes)
      failures.push(`${allowance.name} deferred CSS exceeds its ${allowance.rawBytes} raw / ${allowance.gzipBytes} gzip byte cap`)
    for (const route of [{ name: 'initial entry', entries: [] }, ...budget.routes ?? []]) {
      const initialCss = await measureImportGraph(distDirectory, manifest, ['index.html', ...route.entries], 'css')
      if (initialCss.files.some(file => ownedFiles.includes(file))) failures.push(`${allowance.name} CSS is included in ${route.name}`)
    }
  }
  const css = await measure(files.filter(file => file.endsWith('.css') && !deferredCssFiles.has(path.resolve(file))))

  if (javascript.rawBytes > budget.jsRawBytes) {
    failures.push(`JavaScript raw bundle is ${javascript.rawBytes} bytes; budget is ${budget.jsRawBytes} bytes`)
  }
  if (javascript.gzipBytes > budget.jsGzipBytes) {
    failures.push(`JavaScript gzip bundle is ${javascript.gzipBytes} bytes; budget is ${budget.jsGzipBytes} bytes`)
  }
  if (css.rawBytes > budget.cssRawBytes) {
    failures.push(`CSS raw bundle is ${css.rawBytes} bytes; budget is ${budget.cssRawBytes} bytes`)
  }
  if (css.gzipBytes > budget.cssGzipBytes) {
    failures.push(`CSS gzip bundle is ${css.gzipBytes} bytes; budget is ${budget.cssGzipBytes} bytes`)
  }

  if (failures.length > 0) {
    throw new Error(failures.join('\n'))
  }

  let initial = null
  const routes = []
  let largestDeferredGzipBytes = 0
  if (budget.initialJsGzipBytes !== undefined) {
    const manifest = JSON.parse(await readFile(path.join(distDirectory, '.vite/manifest.json'), 'utf8'))
    initial = await measureImportGraph(distDirectory, manifest, ['index.html'])
    if (initial.gzipBytes > budget.initialJsGzipBytes) failures.push(`Initial JavaScript gzip transfer is ${initial.gzipBytes} bytes; budget is ${budget.initialJsGzipBytes} bytes`)
    for (const route of budget.routes ?? []) {
      const result = await measureImportGraph(distDirectory, manifest, ['index.html', ...route.entries])
      routes.push({ name: route.name, ...result })
      if (result.gzipBytes > route.gzipBytes) failures.push(`${route.name} JavaScript gzip transfer is ${result.gzipBytes} bytes; budget is ${route.gzipBytes} bytes`)
    }
    for (const file of files.filter(file => file.endsWith('.js') && !initial.files.includes(path.relative(distDirectory, file).split(path.sep).join('/')))) {
      largestDeferredGzipBytes = Math.max(largestDeferredGzipBytes, (await measure([file])).gzipBytes)
    }
    if (largestDeferredGzipBytes > budget.deferredChunkGzipBytes) failures.push(`Largest deferred JavaScript gzip chunk is ${largestDeferredGzipBytes} bytes; budget is ${budget.deferredChunkGzipBytes} bytes`)
  }
  if (failures.length > 0) throw new Error(failures.join('\n'))
  return { javascript, css, deferredCss, deferredJs, budget, initial, routes, largestDeferredGzipBytes }

}

function formatKiB(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`
}

async function main() {
  const result = await enforceBundleBudget(path.resolve(process.cwd(), 'dist'))
  for (const entry of result.deferredJs) console.log(`${entry.name}: ${formatKiB(entry.rawBytes)} raw / ${formatKiB(entry.gzipBytes)} gzip in its separately capped deferred entries; shared dependencies remain in core.`)
  console.log(`Initial JavaScript: ${formatKiB(result.initial.gzipBytes)} gzip; largest deferred chunk: ${formatKiB(result.largestDeferredGzipBytes)} gzip.`)
  for (const route of result.routes) console.log(`${route.name}: ${formatKiB(route.gzipBytes)} JavaScript gzip including shared static dependencies.`)
  for (const entry of result.deferredCss) console.log(`${entry.name} deferred CSS: ${entry.rawBytes} raw / ${entry.gzipBytes} gzip bytes; excluded from protected initial routes.`)
  console.log(
    `Bundle budget passed: JS ${formatKiB(result.javascript.rawBytes)} raw / ${formatKiB(result.javascript.gzipBytes)} gzip; `
      + `CSS ${formatKiB(result.css.rawBytes)} raw / ${formatKiB(result.css.gzipBytes)} gzip.`,
  )
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((error) => {
    console.error(`Bundle budget failed:\n${error instanceof Error ? error.message : String(error)}`)
    process.exitCode = 1
  })
}
