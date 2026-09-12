import assert from 'node:assert/strict';
import test from 'node:test';
import { assessReport } from './backend-artifacts.mjs';

test('a metadata-only or incomplete library scan cannot pass', () => {
  assert.throws(() => assessReport({ Results: [{ Type: 'pom', Packages: [] }] }, ['first.jar']), /coverage/);
  assert.throws(() => assessReport({ Results: [{ Type: 'jar', Packages: [
    { FilePath: 'BOOT-INF/lib/first.jar' },
  ] }] }, ['first.jar', 'second.jar']), /second.jar/);
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
