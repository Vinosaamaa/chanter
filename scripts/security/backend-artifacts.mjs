import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export function assessReport(report, expectedLibraries) {
  const results = Array.isArray(report?.Results) ? report.Results : [];
  const libraries = new Set(results.filter(result => result.Type === 'jar')
    .flatMap(result => result.Packages ?? [])
    .map(pkg => pkg.FilePath?.replaceAll('\\', '/').match(/^BOOT-INF\/lib\/([^/]+\.jar)$/)?.[1]).filter(Boolean));
  const missing = expectedLibraries.filter(name => !libraries.has(name));
  if (!expectedLibraries.length || !libraries.size || missing.length) {
    throw new Error(`Incomplete Java library coverage${missing.length ? ': ' + missing.join(', ') : ''}`);
  }
  const vulnerabilities = results.flatMap(result => result.Vulnerabilities ?? []).length;
  const secrets = results.flatMap(result => result.Secrets ?? []).length;
  return { libraries: expectedLibraries.length, vulnerabilities, secrets, passed: vulnerabilities === 0 && secrets === 0 };
}

async function sha256(file) {
  const hash = crypto.createHash('sha256');
  for await (const part of fs.createReadStream(file)) hash.update(part);
  return hash.digest('hex');
}

export function cleanupScanWorkspace(run, cache) {
  const base = fs.realpathSync(cache);
  const target = fs.realpathSync(run);
  if (path.dirname(target) !== base || !path.basename(target).startsWith('run-')) {
    throw new Error('Refusing scan cleanup outside its run directory');
  }
  fs.rmSync(target, { recursive: true });
}

async function scan(scanner) {
  const root = fileURLToPath(new URL('../..', import.meta.url));
  const pom = fs.readFileSync(path.join(root, 'backend/pom.xml'), 'utf8');
  const modulesSection = pom.match(/<modules>([\s\S]*?)<\/modules>/)?.[1] ?? '';
  const modules = [...modulesSection.matchAll(/<module>([a-z-]+-service)<\/module>/g)].map(match => match[1]);
  if (!modules.length) throw new Error('No backend services were selected for security scanning');
  const jar = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar') : 'jar';
  const cache = path.join(root, '.cache/backend-security');
  fs.mkdirSync(cache, { recursive: true });
  const run = fs.mkdtempSync(path.join(cache, 'run-'));
  const summary = [];
  try {
    for (const module of modules) {
      const result = { module, passed: false };
      summary.push(result);
      try {
        const target = path.join(root, 'backend', module, 'target');
        const candidates = fs.readdirSync(target).filter(name => name.endsWith('.jar') && !name.endsWith('-sources.jar') && !name.endsWith('-javadoc.jar'));
        if (candidates.length !== 1) throw new Error('Expected one packaged executable JAR');
        const artifact = path.join(target, candidates[0]);
        result.artifactSha256 = await sha256(artifact);
        const extracted = path.join(run, module);
        fs.mkdirSync(extracted);
        execFileSync(jar, ['xf', artifact], { cwd: extracted, stdio: 'ignore' });
        const libraries = fs.readdirSync(path.join(extracted, 'BOOT-INF/lib')).filter(name => name.endsWith('.jar'));
        const output = path.join(run, `${module}.raw.json`);
        // rootfs analyzes packaged JAR libraries; filesystem mode can inspect only project metadata.
        execFileSync(scanner, ['rootfs', '--quiet', '--skip-version-check', '--scanners', 'vuln,secret',
          '--severity', 'HIGH,CRITICAL', '--format', 'json', '--output', output, extracted], { stdio: 'inherit' });
        const report = JSON.parse(fs.readFileSync(output, 'utf8'));
        Object.assign(result, assessReport(report, libraries));
        // Public reports deliberately omit secret matches and machine-specific scan target paths.
        result.vulnerabilityFindings = report.Results.flatMap(item => item.Vulnerabilities ?? []).map(item => ({
          id: item.VulnerabilityID, package: item.PkgName, installed: item.InstalledVersion,
          fixed: item.FixedVersion ?? null, severity: item.Severity,
        }));
        result.secretRuleIds = [...new Set(report.Results.flatMap(item => item.Secrets ?? []).map(item => item.RuleID))];
        if (result.artifactSha256 !== await sha256(artifact)) throw new Error('Packaged artifact changed during its scan');
        console.log(`${module}: ${result.libraries} libraries, ${result.vulnerabilities} vulnerabilities, ${result.secrets} secrets`);
      } catch (error) {
        result.passed = false;
        result.failure = error.message.startsWith('Incomplete Java library coverage') ? error.message : 'Package preparation or security scan failed';
        console.error(`${module}: ${result.failure}`);
      }
    }
    fs.writeFileSync(path.join(cache, 'summary.json'), JSON.stringify({ scanner: 'Trivy 0.74.0', services: summary }, null, 2) + '\n');
    if (summary.some(result => !result.passed)) throw new Error('Backend security gate failed; inspect the sanitized summary');
  } finally {
    cleanupScanWorkspace(run, cache);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  scan(process.argv[2] ?? 'trivy').catch(error => { console.error(error.message); process.exitCode = 1; });
}
