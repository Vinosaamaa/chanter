import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { privatizeSourceMaps } from './private-source-maps.mjs'

const scratch = path.resolve('.cache/source-map-tests')
fs.mkdirSync(scratch, { recursive: true })
function fixture(t) {
  const root = fs.mkdtempSync(path.join(scratch, 'build-'))
  t.after(() => { assert.ok(root.startsWith(scratch + path.sep)); fs.rmSync(root, { recursive: true }) })
  const dist = path.join(root, 'dist'); const privateOutput = path.join(root, 'private')
  fs.mkdirSync(path.join(dist, 'assets'), { recursive: true })
  fs.writeFileSync(path.join(dist, 'assets/index-Abc123.js'), 'console.log("compiled")')
  fs.writeFileSync(path.join(dist, 'assets/index-Abc123.js.map'), JSON.stringify({ version: 3, file: 'index-Abc123.js',
    sources: ['../../src/main.tsx'], sourcesContent: ['private-source-canary'], names: [], mappings: 'AAAA' }))
  return { dist, privateOutput }
}

test('private map packaging preserves matching bytes and removes all public maps', t => {
  const { dist, privateOutput } = fixture(t)
  const release = 'a'.repeat(40)
  const manifest = privatizeSourceMaps(dist, privateOutput, release)
  assert.equal(manifest.release, release)
  assert.equal(manifest.files.length, 1)
  assert.equal(fs.existsSync(path.join(dist, 'assets/index-Abc123.js.map')), false)
  const served = fs.readFileSync(path.join(dist, 'assets/index-Abc123.js'))
  assert.deepEqual(fs.readFileSync(path.join(privateOutput, 'assets/index-Abc123.js')), served)
  assert.equal(manifest.files[0].scriptSha256, createHash('sha256').update(served).digest('hex'))
  assert.equal(fs.readFileSync(path.join(privateOutput, 'assets/index-Abc123.js.map'), 'utf8').includes('private-source-canary'), true)
  assert.equal(JSON.stringify(manifest).includes('private-source-canary'), false)
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dist, 'browser-error-assets.json'), 'utf8')), {
    release, assets: ['/assets/index-Abc123.js'],
  })
})

test('missing script, public map directives and invalid releases fail before packaging', t => {
  const { dist, privateOutput } = fixture(t)
  assert.throws(() => privatizeSourceMaps(dist, privateOutput, 'unversioned'), /release/)
  fs.writeFileSync(path.join(dist, 'assets/index-Abc123.js'), 'console.log(1)\n//# sourceMappingURL=index-Abc123.js.map')
  assert.throws(() => privatizeSourceMaps(dist, privateOutput, 'a'.repeat(40)), /directive/)
  fs.unlinkSync(path.join(dist, 'assets/index-Abc123.js'))
  assert.throws(() => privatizeSourceMaps(dist, privateOutput, 'a'.repeat(40)), /script/)
  assert.equal(fs.existsSync(path.join(dist, 'assets/index-Abc123.js.map')), true)
})

test('incomplete or invalid embedded sources fail before moving any map', t => {
  const { dist, privateOutput } = fixture(t)
  const mapFile = path.join(dist, 'assets/index-Abc123.js.map')
  const valid = JSON.parse(fs.readFileSync(mapFile, 'utf8'))
  for (const fields of [{ sourcesContent: [] }, { sourcesContent: [null] },
    { sources: ['C:/private/source.ts'] }, { sources: [123] }]) {
    fs.writeFileSync(mapFile, JSON.stringify({ ...valid, ...fields }))
    assert.throws(() => privatizeSourceMaps(dist, privateOutput, 'a'.repeat(40)), /Invalid source map/)
    assert.equal(fs.existsSync(mapFile), true)
    assert.equal(fs.existsSync(path.join(privateOutput, 'manifest.json')), false)
  }
})
