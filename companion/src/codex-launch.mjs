import path from 'node:path';
import { CompanionError } from './codex-app-server.mjs';

const DISABLED = [
  'shell_tool', 'unified_exec', 'shell_snapshot', 'apply_patch_freeform', 'view_image',
  'image_generation', 'apps', 'plugins', 'remote_plugin', 'hooks', 'plugin_hooks',
  'memories', 'memory_tool', 'chronicle', 'multi_agent', 'js_repl', 'code_mode',
  'browser_use', 'computer_use', 'workspace_dependencies', 'goals', 'remote_control',
  'skill_mcp_dependency_install', 'skill_env_var_dependency_prompt', 'skill_search',
];

export function requireSupportedVersion(output) {
  if (typeof output !== 'string' || output.trim() !== 'codex-cli 0.153.4') throw new CompanionError('UNSUPPORTED_CODEX_VERSION');
  return '0.153.4';
}

/** Called only with native installation paths, never values accepted from a website. */
export function codexLaunchPlan({ state, workspace, environment = process.env }) {
  if (!path.isAbsolute(state) || !path.isAbsolute(workspace)
      || path.relative(state, workspace) !== 'empty') throw new CompanionError('INVALID_NATIVE_WORKSPACE');
  const env = {};
  for (const key of ['SystemRoot', 'WINDIR']) if (environment[key]) env[key] = environment[key];
  Object.assign(env, {
    CODEX_HOME: path.join(state, 'provider'), HOME: path.join(state, 'home'),
    USERPROFILE: path.join(state, 'home'), TMP: path.join(state, 'tmp'), TEMP: path.join(state, 'tmp'),
  });
  const config = [
    'approval_policy="never"', 'approvals_reviewer="user"',
    'default_permissions="chanter-study"',
    'permissions.chanter-study.filesystem={":root"="deny",":minimal"="read"}',
    'permissions.chanter-study.network.enabled=false',
    'project_doc_max_bytes=0', 'project_doc_fallback_filenames=[]',
    'web_search="disabled"', 'mcp_servers={}', 'apps._default.enabled=false',
    'skills.bundled.enabled=false', 'skills.include_instructions=false',
    'tools.experimental_request_user_input.enabled=false',
    'features.skip_host_skill_discovery=true', 'include_apps_instructions=false',
    'include_environment_context=false', 'history.persistence="none"',
    'analytics.enabled=false', 'feedback.enabled=false', 'check_for_update_on_startup=false',
    'shell_environment_policy.inherit="none"',
    ...DISABLED.map((name) => `features.${name}=false`),
  ];
  return {
    args: ['app-server', '--stdio', '--strict-config', ...config.flatMap((entry) => ['-c', entry])],
    options: { cwd: workspace, env, shell: false, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] },
  };
}
