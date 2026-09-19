import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

test('database initialization never passes passwords to the psql process arguments', () => {
  const password = 'synthetic-init-credential-with-punctuation-$#';
  const env = { ...process.env, POSTGRES_USER: 'fixture_admin',
    ...Object.fromEntries(['AUTH', 'COMMUNITY', 'MESSAGE', 'MEDIA', 'AGENT', 'SEARCH', 'NOTIFICATION']
      .map(name => [`DB_${name}_PASSWORD`, password])) };
  if (process.platform === 'win32') assert.ok(process.env.BASH_EXECUTABLE, 'Select the native Git Bash executable');
  const bash = process.env.BASH_EXECUTABLE ?? 'bash';
  const command = `psql() {
    for argument do
      case "$argument" in *"$DB_AUTH_PASSWORD"*) echo 'Credential exposed in process arguments' >&2; return 42;; esac
    done
    cat >/dev/null
  }
  export -f psql
  bash infra/production/postgres-init.sh`;
  assert.doesNotThrow(() => execFileSync(bash, ['-c', command], { env, stdio: 'pipe' }));
});
