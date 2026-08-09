import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

export const productionBundleBudget = Object.freeze({
  jsRawBytes: 1_300_000,
  jsGzipBytes: 350_000,
  cssRawBytes: 220_000,
  cssGzipBytes: 45_000,
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

export async function enforceBundleBudget(distDirectory, budget = productionBundleBudget) {
  const files = await assetFiles(distDirectory)
  const javascript = await measure(files.filter((file) => file.endsWith('.js')))
  const css = await measure(files.filter((file) => file.endsWith('.css')))
  const failures = []

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

  return { javascript, css, budget }
}

function formatKiB(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`
}

async function main() {
  const result = await enforceBundleBudget(path.resolve(process.cwd(), 'dist'))
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
