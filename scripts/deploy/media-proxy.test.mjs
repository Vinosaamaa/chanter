import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { assertMediaProxy } from './check-media-proxy.mjs';

test('actual Caddy adaptation preserves the guard and rejects missing or reordered authorization', {
  skip: !process.env.CHANTER_CADDY_BINARY && 'Actual packaged Caddy adaptation is required by release smoke',
}, () => {
  const configuration = JSON.parse(execFileSync(process.env.CHANTER_CADDY_BINARY,
    ['adapt', '--config', 'infra/production/frontend/Caddyfile', '--adapter', 'caddyfile'],
    { encoding: 'utf8', env: { ...process.env, CHANTER_HOSTNAME: 'staging.chanter.test' } }));
  assert.doesNotThrow(() => assertMediaProxy(configuration));
  const missing = JSON.parse(JSON.stringify(configuration).replaceAll('community-service:8080', 'livekit:7880'));
  assert.throws(() => assertMediaProxy(missing));
  const wrongPath = JSON.parse(JSON.stringify(configuration).replaceAll('/livekit/*', '/api/livekit/*'));
  assert.throws(() => assertMediaProxy(wrongPath));
  const moved = structuredClone(configuration);
  const reorder = value => {
    if (!value || typeof value !== 'object') return;
    if (Array.isArray(value.handle) && value.handle.length === 5) [value.handle[1], value.handle[4]] = [value.handle[4], value.handle[1]];
    for (const child of Object.values(value)) Array.isArray(child) ? child.forEach(reorder) : reorder(child);
  };
  reorder(moved);
  assert.throws(() => assertMediaProxy(moved));
});
