import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { privatizeSourceMaps } from './private-source-maps.mjs'
import { verifyPrivateMaps, sourceMapUpload } from '../../scripts/upload-source-maps.mjs'

const scratch = path.resolve('.cache/upload-map-tests')
fs.mkdirSync(scratch, { recursive: true })
const release = 'a'.repeat(40)
function fixture(t) {
  const root = fs.mkdtempSync(path.join(scratch, 'release-'))
  t.after(() => { assert.ok(root.startsWith(scratch + path.sep)); fs.rmSync(root, { recursive: true }) })
  const dist = path.join(root, 'dist'), artifact = path.join(root, 'private')
  fs.mkdirSync(path.join(dist, 'assets'), { recursive: true })
  fs.writeFileSync(path.join(dist, 'assets/index-Abc12345.js'), 'console.log(1)')
  fs.writeFileSync(path.join(dist, 'assets/index-Abc12345.js.map'), JSON.stringify({ version: 3,
    sources: ['../../src/main.ts'], sourcesContent: ['const value = 1'], names: [], mappings: 'AAAA' }))
  privatizeSourceMaps(dist, artifact, release)
  return { dist, artifact }
}

test('upload requires exact served bytes, release and private inventory before invoking a provider', t => {
  const { dist, artifact } = fixture(t)
  assert.equal(verifyPrivateMaps(artifact, dist, release).files.length, 1)
  assert.throws(() => verifyPrivateMaps(artifact, dist, 'b'.repeat(40)), /source-map artifact/)
  fs.writeFileSync(path.join(artifact, 'assets/private.env'), 'private-canary')
  assert.throws(() => verifyPrivateMaps(artifact, dist, release), /source-map artifact/)
  fs.unlinkSync(path.join(artifact, 'assets/private.env'))
  fs.writeFileSync(path.join(dist, 'assets/index-Abc12345.js'), 'different served release')
  assert.throws(() => verifyPrivateMaps(artifact, dist, release), /source-map artifact/)
})

test('private upload configuration never puts credentials in arguments or enables a foreign receiver', () => {
  const env = { SENTRY_AUTH_TOKEN: 'private-token-canary', SENTRY_ORG: 'fixture-org', SENTRY_PROJECT: 'fixture-project' }
  const upload = sourceMapUpload(release, env)
  assert.equal(JSON.stringify(upload.args).includes(env.SENTRY_AUTH_TOKEN), false)
  assert.equal(upload.env.SENTRY_AUTH_TOKEN, env.SENTRY_AUTH_TOKEN)
  assert.ok(upload.args.includes('--strict'))
  assert.ok(upload.args.includes('--no-rewrite'))
  assert.ok(upload.args.includes('~/assets'))
  assert.throws(() => sourceMapUpload(release, { ...env, SENTRY_URL: 'https://foreign.example' }), /configuration/)
  assert.throws(() => sourceMapUpload(release, { ...env, SENTRY_AUTH_TOKEN: '' }), /configuration/)
})
