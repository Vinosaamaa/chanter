#!/usr/bin/env node
// Stable systemd entry point; each run follows the accepted release receipt.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

try {
  const [state, type] = process.argv.slice(2);
  if (!state || !path.isAbsolute(state) || !['full', 'incr', 'check'].includes(type)) throw new Error();
  const current = JSON.parse(fs.readFileSync(path.join(state, 'current.json'), 'utf8'));
  if (!path.isAbsolute(current.bundleDir) || !/^[a-f0-9]{40}$/.test(current.commit ?? '')) throw new Error();
  execFileSync(process.execPath, [path.join(current.bundleDir, 'scripts/deploy/host.mjs'), 'backup', state, type],
    { stdio: 'inherit' });
} catch {
  console.error('Scheduled Chanter backup failed. Inspect the private deployment and backup status.');
  process.exitCode = 1;
}
