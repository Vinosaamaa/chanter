import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { codexLaunchPlan, requireSupportedVersion } from '../src/codex-launch.mjs';

test('only inspected native versions are eligible', () => {
  assert.equal(requireSupportedVersion('codex-cli 0.153.4\n'), '0.153.4');
  for (const version of ['codex-cli 0.153.3', 'codex-cli 0.154.0', 'codex-cli 0.153.4\nprivate content', '']) {
    assert.throws(() => requireSupportedVersion(version), { code: 'UNSUPPORTED_CODEX_VERSION' });
  }
});

test('launch uses a separate provider home and does not inherit tokens, proxies, or agent context', () => {
  const state = path.resolve('.cache/native-test-state');
  const workspace = path.join(state, 'empty');
  const plan = codexLaunchPlan({ state, workspace, environment: {
    SystemRoot: 'C:\\Windows', PATH: 'private-path', OPENAI_API_KEY: 'private-api-key',
    CODEX_HOME: 'private-codex-home', CODEX_THREAD_ID: 'private-thread', HTTP_PROXY: 'private-proxy',
    NODE_OPTIONS: '--require malicious.cjs',
  } });
  assert.equal(plan.options.shell, false);
  assert.equal(plan.options.windowsHide, true);
  assert.equal(plan.options.cwd, workspace);
  assert.deepEqual(plan.options.env, {
    SystemRoot: 'C:\\Windows', CODEX_HOME: path.join(state, 'provider'),
    CODEX_SQLITE_HOME: path.join(state, 'sqlite'),
    HOME: path.join(state, 'home'), USERPROFILE: path.join(state, 'home'),
    TMP: path.join(state, 'tmp'), TEMP: path.join(state, 'tmp'),
  });
  assert.deepEqual(plan.args.slice(0, 4), ['app-server', '--stdio', '--strict-config', '-c']);
  assert.ok(plan.args.includes('features.shell_tool=false'));
  assert.ok(plan.args.includes('features.skip_host_skill_discovery=true'));
  assert.ok(plan.args.includes('project_doc_max_bytes=0'));
  assert.ok(plan.args.includes('default_permissions="chanter-study"'));
  assert.ok(!plan.args.some((arg) => arg.includes('danger-full-access')));
  assert.throws(() => codexLaunchPlan({ state, workspace: path.dirname(state), environment: {} }),
    { code: 'INVALID_NATIVE_WORKSPACE' });
});
