#!/usr/bin/env node
/**
 * SEC-10: fail the production frontend build if the DEV demo harness leaked into dist/.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const distDir = join(process.cwd(), 'dist')
const forbidden = [
  'chanter-dev-demo',
  'dev-demo-owner@chanter.local',
  'bootstrapDemoPersonas',
  'DevDemoLazyRoute',
]

function collectFiles(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) {
      collectFiles(path, out)
    } else if (/\.(js|css|html|map)$/.test(name)) {
      out.push(path)
    }
  }
  return out
}

const files = collectFiles(distDir)
if (files.length === 0) {
  console.error('SEC-10 check: no files under dist/')
  process.exit(1)
}

const haystack = files.map((file) => readFileSync(file, 'utf8')).join('\n')
const hits = forbidden.filter((needle) => haystack.includes(needle))
if (hits.length > 0) {
  console.error('SEC-10 check failed — production bundle still contains:', hits.join(', '))
  process.exit(1)
}

console.log('SEC-10 check passed: DEV demo harness not present in dist/')
