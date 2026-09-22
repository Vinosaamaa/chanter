import test from 'node:test';
import assert from 'node:assert/strict';
import { errorEnvironment, browserErrorConfiguration } from './errors.mjs';

test('browser receiver is explicit and independent from private backend settings', () => {
  const release = 'a'.repeat(40);
  const dsn = `https://${'b'.repeat(32)}@o1.ingest.us.sentry.io/1`;
  assert.deepEqual(browserErrorConfiguration({ CHANTER_ERRORS_DSN: dsn }, release, 'production'), { config: {}, origin: '' });
  assert.deepEqual(browserErrorConfiguration({ CHANTER_BROWSER_ERRORS_DSN: dsn }, release, 'production'), {
    config: { dsn, release, environment: 'production' }, origin: 'https://o1.ingest.us.sentry.io',
  });
  assert.throws(() => browserErrorConfiguration({ CHANTER_BROWSER_ERRORS_DSN: dsn }, 'private-canary', 'production'), /release/);
  assert.throws(() => browserErrorConfiguration({ CHANTER_BROWSER_ERRORS_DSN: dsn }, release, 'private-canary'), /environment/);
  assert.throws(() => browserErrorConfiguration({ CHANTER_BROWSER_ERRORS_DSN: dsn + '?private-canary' }, release, 'production'), /valid Sentry/);
});

test('private errors require an explicit valid receiver without exposing invalid values', () => {
  assert.deepEqual(errorEnvironment({}), { CHANTER_ERRORS_ENABLED: 'false' });
  const dsn = `https://${'a'.repeat(32)}@o1.ingest.us.sentry.io/1`;
  assert.deepEqual(errorEnvironment({ CHANTER_ERRORS_DSN: dsn }), { CHANTER_ERRORS_ENABLED: 'true', CHANTER_ERRORS_DSN: dsn });
  assert.equal(errorEnvironment({ CHANTER_ERRORS_DSN: dsn.replace('sentry.io', 'SENTRY.IO') }).CHANTER_ERRORS_DSN, dsn);
  for (const value of [dsn.replace('https:', 'http:'), dsn + '?private-canary', dsn + '#private-canary',
    dsn.replace('@', ':private-canary@'), dsn.replace('sentry.io', 'private-canary.example'), dsn + '\nprivate-canary']) {
    assert.throws(() => errorEnvironment({ CHANTER_ERRORS_DSN: value }),
      { message: 'Error reporting requires a valid Sentry HTTPS DSN' });
  }
});
