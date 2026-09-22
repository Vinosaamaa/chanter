import test from 'node:test';
import assert from 'node:assert/strict';
import { errorEnvironment } from './errors.mjs';

test('private errors require an explicit valid receiver without exposing invalid values', () => {
  assert.deepEqual(errorEnvironment({}), { CHANTER_ERRORS_ENABLED: 'false' });
  const dsn = `https://${'a'.repeat(32)}@o1.ingest.us.sentry.io/1`;
  assert.deepEqual(errorEnvironment({ CHANTER_ERRORS_DSN: dsn }), { CHANTER_ERRORS_ENABLED: 'true', CHANTER_ERRORS_DSN: dsn });
  for (const value of [dsn.replace('https:', 'http:'), dsn + '?private-canary', dsn + '#private-canary',
    dsn.replace('@', ':private-canary@'), dsn.replace('sentry.io', 'private-canary.example'), dsn + '\nprivate-canary']) {
    assert.throws(() => errorEnvironment({ CHANTER_ERRORS_DSN: value }),
      { message: 'Error reporting requires a valid Sentry HTTPS DSN' });
  }
});
