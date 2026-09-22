import fs from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const pins = JSON.parse(fs.readFileSync(path.join(root, 'infra/monitoring/sentry-cli.json'), 'utf8'))
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const invalidArtifact = () => { throw new Error('Invalid or mismatched private source-map artifact') }
function inventory(directory) {
  const result = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isSymbolicLink()) invalidArtifact()
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) result.push(...inventory(file))
    else if (entry.isFile()) result.push(file)
    else invalidArtifact()
    if (result.length > 1001) invalidArtifact()
  }
  return result
}

export function verifyPrivateMaps(artifactDirectory, distDirectory, release) {
  try {
    if (!/^[a-f0-9]{40}$/.test(release)) invalidArtifact()
    const artifact = path.resolve(artifactDirectory), dist = path.resolve(distDirectory)
    if (artifact === dist || artifact.startsWith(dist + path.sep) || fs.realpathSync(artifact) !== artifact || fs.realpathSync(dist) !== dist) invalidArtifact()
    const files = inventory(artifact)
    const manifestFile = path.join(artifact, 'manifest.json')
    if (fs.statSync(manifestFile).size > 1024 * 1024) invalidArtifact()
    const manifest = JSON.parse(fs.readFileSync(manifestFile, 'utf8'))
    if (manifest.version !== 1 || manifest.release !== release || !Array.isArray(manifest.files)
      || manifest.files.length < 1 || manifest.files.length > 500) invalidArtifact()
    const expected = new Set([manifestFile])
    let total = 0
    for (const item of manifest.files) {
      if (!/^\/assets\/[A-Za-z0-9_-]{1,180}\.js$/.test(item.publicPath)
        || item.mapPath !== item.publicPath.slice(1) + '.map'
        || !/^[a-f0-9]{64}$/.test(item.scriptSha256) || !/^[a-f0-9]{64}$/.test(item.mapSha256)) invalidArtifact()
      const script = path.join(artifact, item.publicPath.slice(1)), map = path.join(artifact, item.mapPath)
      const served = path.join(dist, item.publicPath.slice(1))
      if (expected.has(script) || !files.includes(script) || !files.includes(map)) invalidArtifact()
      for (const file of [script, map, served]) {
        const stat = fs.lstatSync(file)
        if (!stat.isFile() || stat.size > 20 * 1024 * 1024) invalidArtifact()
        total += stat.size
        if (total > 100 * 1024 * 1024) invalidArtifact()
      }
      if (sha(fs.readFileSync(script)) !== item.scriptSha256 || sha(fs.readFileSync(served)) !== item.scriptSha256
        || sha(fs.readFileSync(map)) !== item.mapSha256) invalidArtifact()
      const sourceMap = JSON.parse(fs.readFileSync(map, 'utf8'))
      if (sourceMap.version !== 3 || !Array.isArray(sourceMap.sources) || !Array.isArray(sourceMap.sourcesContent)
        || sourceMap.sources.length !== sourceMap.sourcesContent.length
        || sourceMap.sources.some(source => typeof source !== 'string' || /^(?:[A-Za-z]:|file:|\/|\\)/.test(source))
        || sourceMap.sourcesContent.some(source => typeof source !== 'string')) invalidArtifact()
      expected.add(script); expected.add(map)
    }
    if (files.length !== expected.size || files.some(file => !expected.has(file))) invalidArtifact()
    const publicManifest = JSON.parse(fs.readFileSync(path.join(dist, 'browser-error-assets.json'), 'utf8'))
    if (publicManifest.release !== release || JSON.stringify(publicManifest.assets) !== JSON.stringify(manifest.files.map(file => file.publicPath))) invalidArtifact()
    return manifest
  } catch { invalidArtifact() }
}

export function sourceMapUpload(release, settings) {
  const { SENTRY_AUTH_TOKEN: token, SENTRY_ORG: org, SENTRY_PROJECT: project } = settings
  const url = settings.SENTRY_URL || 'https://sentry.io/'
  if (!/^[a-f0-9]{40}$/.test(release) || typeof token !== 'string' || !token || token.length > 4096 || /[\s\0]/.test(token)
    || !/^[a-z0-9_-]{1,100}$/.test(org ?? '') || !/^[a-z0-9_-]{1,100}$/.test(project ?? '')
    || !['https://sentry.io/', 'https://us.sentry.io/', 'https://de.sentry.io/'].includes(url)) throw new Error('Invalid private source-map upload configuration')
  return {
    args: ['--url', url, '--log-level', 'error', 'sourcemaps', 'upload', '--org', org, '--project', project, '--release', release,
      '--url-prefix', '~/assets', '--no-rewrite', '--validate', '--strict', '--wait-for', '60'],
    env: { SENTRY_AUTH_TOKEN: token, SENTRY_ORG: org, SENTRY_PROJECT: project, SENTRY_URL: url,
      SENTRY_DISABLE_UPDATE_CHECK: 'true', SENTRY_LOAD_DOTENV: '0', SENTRY_LOG_LEVEL: 'error', SENTRY_NO_PROGRESS_BAR: '1' },
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const [binary, artifact, dist, release] = process.argv.slice(2)
    const pin = pins.binaries[`${process.platform}-${process.arch}`]
    if (!pin || !binary || !artifact || !dist || sha(fs.readFileSync(binary)) !== pin.sha256) throw new Error()
    const manifest = verifyPrivateMaps(artifact, dist, release)
    const command = sourceMapUpload(release, process.env)
    execFileSync(path.resolve(binary), [...command.args, path.join(path.resolve(artifact), 'assets')], {
      cwd: path.resolve(artifact), timeout: 180_000, maxBuffer: 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...Object.fromEntries(['PATH', 'SystemRoot', 'TEMP', 'TMP'].filter(key => process.env[key]).map(key => [key, process.env[key]])), ...command.env },
    })
    fs.writeFileSync(path.join(path.dirname(path.resolve(artifact)), `upload-${release}-${process.arch}.json`), JSON.stringify({
      version: 1, release, scripts: manifest.files.length, status: 'upload-processed', symbolicationVerified: false,
    }) + '\n', { mode: 0o600 })
    console.log('Private source-map upload processed; an actual symbolication check is still required.')
  } catch {
    console.error('Private source-map upload failed. Preserve the private artifact; no provider output was published.')
    process.exitCode = 1
  }
}
