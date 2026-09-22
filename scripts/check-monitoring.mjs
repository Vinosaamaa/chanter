import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const executable = process.argv[2];
if (!executable) throw new Error('Usage: node scripts/check-monitoring.mjs PATH_TO_PINNED_PROMTOOL');
const dashboard = JSON.parse(fs.readFileSync('infra/monitoring/operations-dashboard.json', 'utf8'));
const rules = dashboard.panels.flatMap(panel => (panel.targets ?? []).map((target, index) => ({
  record: `chanter_dashboard_validation_${panel.id}_${index}`,
  expr: target.expr.replaceAll('$environment', 'production'),
})));
if (rules.length === 0 || rules.some(rule => rule.expr.includes('$'))) throw new Error('Dashboard query substitution failed');
fs.mkdirSync('.cache/monitoring-validation', { recursive: true });
const directory = fs.mkdtempSync('.cache/monitoring-validation/queries-');
const queryFile = path.join(directory, 'dashboard-rules.yml');
fs.writeFileSync(queryFile, JSON.stringify({ groups: [{ name: 'dashboard-validation', rules }] }));
// Parse the actual dashboard queries with Prometheus, not a second home-grown parser.
for (const args of [['check', 'rules', queryFile, 'infra/monitoring/alerts.yml'],
  ['test', 'rules', 'infra/monitoring/alerts.test.yml']]) {
  execFileSync(executable, args, { stdio: 'inherit', timeout: 60000 });
}
