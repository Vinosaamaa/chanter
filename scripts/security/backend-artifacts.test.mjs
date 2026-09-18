import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assessReport, cleanupScanWorkspace } from './backend-artifacts.mjs';

test('a metadata-only or incomplete library scan cannot pass', () => {
  assert.throws(() => assessReport({ Results: [{ Type: 'pom', Packages: [] }] }, ['first.jar']), /coverage/);
  assert.throws(() => assessReport({ Results: [{ Type: 'jar', Packages: [
    { FilePath: 'BOOT-INF/lib/first.jar' },
  ] }] }, ['first.jar', 'second.jar']), /second.jar/);
  for (const candidate of ['BOOT-INF/classes/first.jar', 'other/BOOT-INF/lib/first.jar']) {
    assert.throws(() => assessReport({ Results: [{ Type: 'jar', Packages: [
      { FilePath: candidate },
    ] }] }, ['first.jar']), /coverage/);
  }
});

test('complete coverage retains vulnerability and secret failures', () => {
  const result = { Type: 'jar', Packages: [{ FilePath: 'BOOT-INF/lib/first.jar' }],
    Vulnerabilities: [{ Severity: 'HIGH', VulnerabilityID: 'CVE-EXAMPLE' }],
    Secrets: [{ Severity: 'CRITICAL', RuleID: 'fixture-secret' }] };
  const verdict = assessReport({ Results: [result] }, ['first.jar']);
  assert.equal(verdict.libraries, 1);
  assert.equal(verdict.vulnerabilities, 1);
  assert.equal(verdict.secrets, 1);
  assert.equal(verdict.passed, false);
});

test('a complete clean library scan passes', () => {
  const verdict = assessReport({ Results: [{ Type: 'jar', Packages: [
    { FilePath: 'BOOT-INF/lib/first.jar' }, { FilePath: 'BOOT-INF/lib/second.jar' },
  ] }] }, ['first.jar', 'second.jar']);
  assert.equal(verdict.passed, true);
  assert.equal(verdict.libraries, 2);
});

test('cleanup removes only its verified run directory and preserves the summary and adjacent files', () => {
  const cache = fileURLToPath(new URL('../../.cache/backend-security-tests/', import.meta.url));
  fs.mkdirSync(cache, { recursive: true });
  const run = fs.mkdtempSync(path.join(cache, 'run-'));
  const summary = path.join(cache, 'summary.json');
  fs.writeFileSync(summary, '{}');
  fs.writeFileSync(path.join(run, 'private.raw.json'), 'fixture');
  cleanupScanWorkspace(run, cache);
  assert.equal(fs.existsSync(run), false);
  assert.equal(fs.readFileSync(summary, 'utf8'), '{}');
  assert.throws(() => cleanupScanWorkspace(cache, cache), /outside/);
  assert.equal(fs.existsSync(summary), true);
});
