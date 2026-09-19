import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
const require = createRequire(new URL('../../frontend/package.json', import.meta.url))
const { build } = await import(pathToFileURL(require.resolve('vite')).href)
const output = path.resolve('../.product/moderation-browser/client')
await mkdir(output, { recursive: true })
await build({ configFile: false, logLevel: 'warn', build: { outDir: output, emptyOutDir: false,
  lib: { entry: path.resolve('e2e/moderation-audio-client.ts'), formats: ['es'], fileName: () => 'audio.js' } } })
await writeFile(path.join(output, 'audio.html'), '<!doctype html><html lang="en"><meta charset="utf-8"><title>Moderation audio proof</title><h1>Moderation audio verification</h1><p id="status">Not connected</p><script type="module" src="./audio.js"></script></html>')
