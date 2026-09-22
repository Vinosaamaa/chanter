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
const fixture = JSON.parse(fs.readFileSync('infra/monitoring/dashboard.fixture.json', 'utf8'));
const checks = dashboard.panels.flatMap(panel => (panel.targets ?? []).flatMap(target => {
  const expected = fixture.panels[panel.id];
  if (!expected) throw new Error(`Dashboard panel ${panel.id} lacks metric evidence`);
  return ['count', 'sum'].map(operation => ({
    expr: `${operation}((${target.expr.replaceAll('$environment', 'production')}))`,
    eval_time: '10m', exp_samples: [{ labels: '{}', value: expected[operation] }],
  }));
}));
const seriesFor = environment => fixture.series.map(([series, values]) => ({
  series: series.replace('{', `{deployment_environment_name="${environment}",`), values,
}));
const fixtureFile = path.join(directory, 'dashboard.test.yml');
fs.writeFileSync(fixtureFile, JSON.stringify({ evaluation_interval: '1m', fuzzy_compare: true, tests: [
  { name: 'every dashboard query evaluates known metric values and excludes staging', interval: '1m',
    input_series: [...seriesFor('production'), ...seriesFor('staging')], promql_expr_test: checks },
  { name: 'missing telemetry stays unknown', interval: '1m', input_series: [],
    promql_expr_test: checks.map(check => ({ ...check, exp_samples: [] })) },
] }));
// Parse the actual dashboard queries with Prometheus, not a second home-grown parser.
for (const args of [['check', 'rules', queryFile, 'infra/monitoring/alerts.yml'],
  ['test', 'rules', 'infra/monitoring/alerts.test.yml', fixtureFile]]) {
  execFileSync(executable, args, { stdio: 'inherit', timeout: 60000 });
}
