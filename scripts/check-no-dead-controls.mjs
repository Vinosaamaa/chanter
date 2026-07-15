#!/usr/bin/env node
/**
 * Heuristic no-dead-controls gate (#103).
 * Ensures intentionally disabled primary controls are documented in the inventory.
 */
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..')
const inventoryPath = path.join(root, 'docs/operations/no-dead-controls-inventory.md')
const inventory = fs.readFileSync(inventoryPath, 'utf8')

const scanRoots = [
  path.join(root, 'frontend/src/features/auth'),
  path.join(root, 'frontend/src/features/marketing'),
  path.join(root, 'frontend/src/features/v2-shell/pages'),
]

const disabledHits = []

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full)
      continue
    }
    if (!/\.(tsx|ts|jsx|js)$/.test(entry.name)) continue
    const text = fs.readFileSync(full, 'utf8')
    const lines = text.split('\n')
    lines.forEach((line, index) => {
      if (!/\bdisabled\b/.test(line)) return
      if (!/<button|type=\"button\"|google-button|Mark helpful|Continue with Google/.test(line)
          && !/disabled[=:{]/.test(line)) {
        return
      }
      // Skip dynamic disabled={isSubmitting} style loading states
      if (/disabled=\{[^}]*isSubmitting|disabled=\{[^}]*isLoading|disabled=\{[^}]*isPosting|disabled=\{[^}]*isModerating|disabled=\{[^}]*invoking|disabled=\{[^}]*streamPhase|disabled=\{[^}]*markingHelpful|disabled=\{[^}]*addingToQueue/.test(line)) {
        return
      }
      if (/disabled=\{Boolean\(questions\.selectedAnswer\.helpfulMarked\)/.test(line)) {
        disabledHits.push({ file: full, line: index + 1, snippet: line.trim() })
        return
      }
      if (/google-button/.test(line) || /Continue with Google/.test(lines.slice(Math.max(0, index - 2), index + 3).join('\n'))) {
        disabledHits.push({ file: full, line: index + 1, snippet: line.trim() })
      }
    })
  }
}

for (const dir of scanRoots) {
  if (fs.existsSync(dir)) walk(dir)
}

const missing = []
for (const hit of disabledHits) {
  const rel = path.relative(root, hit.file)
  if (inventory.includes('Continue with Google') && /google-button|Continue with Google/.test(hit.snippet + rel)) {
    continue
  }
  if (inventory.includes('Mark helpful') && /helpfulMarked|Mark helpful/.test(hit.snippet)) {
    continue
  }
  missing.push(hit)
}

if (missing.length > 0) {
  console.error('Undocumented disabled primary controls:')
  for (const hit of missing) {
    console.error(`- ${path.relative(root, hit.file)}:${hit.line} ${hit.snippet}`)
  }
  console.error('Add a row to docs/operations/no-dead-controls-inventory.md')
  process.exit(1)
}

console.log(`no-dead-controls OK (${disabledHits.length} known disabled primary controls checked)`)
