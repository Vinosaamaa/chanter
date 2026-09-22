import fs from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

const digest = bytes => createHash('sha256').update(bytes).digest('hex')
function filesBelow(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    if (entry.isSymbolicLink()) throw new Error('Source-map packaging rejects symbolic links')
    const file = path.join(directory, entry.name)
    return entry.isDirectory() ? filesBelow(file) : [file]
  })
}

export function privatizeSourceMaps(distDirectory, privateDirectory, release) {
  if (!/^[a-f0-9]{40}$/.test(release)) throw new Error('Source maps require an immutable Git release')
  const dist = path.resolve(distDirectory); const output = path.resolve(privateDirectory)
  if (output === dist || output.startsWith(dist + path.sep)) throw new Error('Source maps require private output outside public assets')
  const files = filesBelow(dist)
  const maps = files.filter(file => file.endsWith('.map'))
  if (maps.length === 0) throw new Error('Hidden JavaScript source maps are missing')
  const prepared = maps.map(file => {
    const relative = path.relative(dist, file).replaceAll('\\', '/')
    if (!/^assets\/[A-Za-z0-9_-]{1,180}\.js\.map$/.test(relative)) throw new Error('Unexpected public source-map path')
    const script = file.slice(0, -4)
    if (!fs.existsSync(script) || !fs.statSync(script).isFile()) throw new Error('Source map has no matching script')
    const scriptBytes = fs.readFileSync(script); const mapBytes = fs.readFileSync(file)
    if (/\/\/[#@]\s*sourceMappingURL=|\/\*[#@]\s*sourceMappingURL=/.test(scriptBytes.toString('utf8')))
      throw new Error('Public source-map directive must be absent')
    const map = JSON.parse(mapBytes.toString('utf8'))
    if (map.version !== 3 || !Array.isArray(map.sources) || !Array.isArray(map.sourcesContent)
        || typeof map.mappings !== 'string') throw new Error('Invalid source map')
    return { file, script, relative, scriptBytes, mapBytes }
  })
  // Validate the complete set before moving anything. A failed build must not
  // produce a success manifest or an apparently complete private artifact.
  fs.mkdirSync(path.join(output, 'assets'), { recursive: true })
  for (const item of prepared) {
    fs.writeFileSync(path.join(output, item.relative.slice(0, -4)), item.scriptBytes)
    fs.renameSync(item.file, path.join(output, item.relative))
  }
  if (filesBelow(dist).some(file => file.endsWith('.map'))) throw new Error('Public source maps remain')
  const manifest = { version: 1, release, files: prepared.map(item => ({
    publicPath: '/' + item.relative.slice(0, -4), mapPath: item.relative,
    scriptSha256: digest(item.scriptBytes), mapSha256: digest(item.mapBytes),
  })) }
  fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
  // Public code locations only. No source text, local paths or map locations.
  fs.writeFileSync(path.join(dist, 'browser-error-assets.json'), JSON.stringify({
    release, assets: manifest.files.map(file => file.publicPath),
  }) + '\n')
  return manifest
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const release = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  const root = path.resolve('.cache/private-source-maps')
  fs.mkdirSync(root, { recursive: true })
  const output = fs.mkdtempSync(path.join(root, release + '-'))
  const manifest = privatizeSourceMaps('dist', output, release)
  console.log(`Private source maps prepared for ${manifest.release}: ${manifest.files.length} matching scripts`)
}
