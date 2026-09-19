import test from 'node:test';
import assert from 'node:assert/strict';
import { telemetryEnvironment } from './telemetry.mjs';

test('trace export is disabled until both private receiver settings are configured', () => {
  assert.deepEqual(telemetryEnvironment({}), { CHANTER_TELEMETRY_ENABLED: 'false', OTEL_TRACES_EXPORTER: 'none', OTEL_METRICS_EXPORTER: 'none' });
  for (const endpoint of ['', 'http://collector.test/v1/traces', 'https://user:secret@collector.test/v1/traces',
    'https://collector.test/v1/traces?key=private', 'https://collector.test/logs']) {
    assert.throws(() => telemetryEnvironment({ CHANTER_TELEMETRY_ENDPOINT: endpoint, CHANTER_TELEMETRY_AUTHORIZATION: 'private-canary' }),
      error => error.message.includes('private-canary') === false);
  }
  const env = telemetryEnvironment({ CHANTER_TELEMETRY_ENDPOINT: 'https://collector.test/otlp/v1/traces',
    CHANTER_TELEMETRY_AUTHORIZATION: 'Bearer test-secret' });
  assert.equal(env.OTEL_EXPORTER_OTLP_TRACES_HEADERS, 'Authorization=Bearer%20test-secret');
  assert.equal(env.OTEL_EXPORTER_OTLP_METRICS_HEADERS, env.OTEL_EXPORTER_OTLP_TRACES_HEADERS);
  assert.equal(env.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, 'https://collector.test/otlp/v1/metrics');
  assert.equal(env.OTEL_METRIC_EXPORT_INTERVAL, '60000');
  assert.equal(env.OTEL_TRACES_SAMPLER_ARG, '0.05');
  assert.equal(env.OTEL_JAVAAGENT_EXTENSIONS, '/app/telemetry/privacy.jar');
});
