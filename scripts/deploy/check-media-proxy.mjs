import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// Inspect the real Caddy adapter output, including directive ordering.
export function assertMediaProxy(configuration) {
  const routes = [];
  const visit = value => {
    if (!value || typeof value !== 'object') return;
    if (Array.isArray(value.match) && value.match.some(match => match.path?.includes('/livekit/*'))) routes.push(value);
    for (const child of Object.values(value)) {
      if (Array.isArray(child)) child.forEach(visit); else visit(child);
    }
  };
  visit(configuration);
  assert.equal(routes.length, 1, 'Exactly one guarded LiveKit route is required');
  const flatten = route => (route.handle ?? []).flatMap(handler => handler.handler === 'subroute'
    ? handler.routes.flatMap(flatten) : [handler]);
  const handlers = flatten(routes[0]);
  assert.deepEqual(handlers.map(handler => handler.handler), ['headers', 'reverse_proxy', 'headers', 'rewrite', 'reverse_proxy']);
  const [token, authority, remove, strip, proxy] = handlers;
  assert.deepEqual(token.request.set['X-Chanter-Livekit-Token'], ['{http.request.uri.query.access_token}']);
  assert.deepEqual(authority.upstreams, [{ dial: 'community-service:8080' }]);
  assert.deepEqual(authority.rewrite, { method: 'GET', uri: '/internal/v1/media/join-authorization' });
  assert.deepEqual(authority.handle_response.map(response => response.match), [{ status_code: [2] }]);
  assert.equal(authority.transport.dial_timeout, 2_000_000_000);
  assert.equal(authority.transport.response_header_timeout, 5_000_000_000);
  assert.deepEqual(remove.request.delete, ['X-Chanter-LiveKit-Token']);
  assert.equal(strip.strip_path_prefix, '/livekit');
  assert.deepEqual(proxy.upstreams, [{ dial: 'livekit:7880' }]);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  assertMediaProxy(JSON.parse(fs.readFileSync(process.argv[2], 'utf8').replace(/^\uFEFF/, '')));
  console.log('Packaged LiveKit authorization order and private upstreams verified.');
}
