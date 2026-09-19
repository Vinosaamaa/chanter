import http from 'node:http';
import https from 'node:https';
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { gunzipSync } from 'node:zlib';

export const CANARY = 'vector-private-canary-247';
const MAX_BODY = 1024 * 1024;

/** Test-only loopback OTLP receiver. Persist counts, never exported payloads. */
export async function startReceiver({ tls, onReport = () => {} } = {}) {
  const state = { port: 0, traces: 0, metrics: 0, rejected: 0, canaryDetected: false, httpObserved: false, jvmObserved: false, bytes: 0 };
  const report = () => onReport({ ...state });
  const handler = (request, response) => {
    const chunks = [];
    let size = 0, finished = false;
    const reject = () => {
      if (finished) return;
      finished = true; state.rejected++; report();
      response.writeHead(400); response.end();
    };
    if (request.method !== 'POST' || !['/v1/traces', '/v1/metrics'].includes(request.url)) {
      reject(); request.resume(); return;
    }
    request.on('error', reject);
    request.on('data', chunk => {
      if (finished) return;
      size += chunk.length;
      if (size > MAX_BODY) { reject(); request.destroy(); return; }
      chunks.push(chunk);
    });
    request.on('end', () => {
      if (finished) return;
      try {
        if (request.headers['content-type']?.split(';')[0] !== 'application/x-protobuf') { reject(); return; }
        let body = Buffer.concat(chunks);
        if (request.headers['content-encoding'] === 'gzip') body = gunzipSync(body, { maxOutputLength: MAX_BODY });
        else if (request.headers['content-encoding']) { reject(); return; }
        if (body.includes(Buffer.from(CANARY))) { state.canaryDetected = true; reject(); return; }
        // Protobuf strings retain their UTF-8 bytes on the wire; no payload is logged or persisted.
        if (!body.includes(Buffer.from('agent-service'))) { reject(); return; }
        if (request.url === '/v1/traces' && body.includes(Buffer.from('HTTP GET'))) state.httpObserved = true;
        if (request.url === '/v1/metrics' && body.includes(Buffer.from('jvm.memory.used'))) state.jvmObserved = true;
        state[request.url === '/v1/traces' ? 'traces' : 'metrics']++;
        state.bytes += body.length; finished = true; report();
        response.writeHead(200, { 'Content-Type': 'application/x-protobuf' }); response.end();
      } catch { reject(); }
    });
  };
  const server = tls ? https.createServer(tls, handler) : http.createServer(handler);
  server.requestTimeout = 2500; server.headersTimeout = 2500; server.keepAliveTimeout = 1000;
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  state.port = server.address().port; report();
  return { snapshot: () => ({ ...state }), close: () => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); }) };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [reportPath, certificate, key] = process.argv.slice(2);
  if (!reportPath || !certificate || !key) throw new Error('Expected report path and owned fixture certificate/key');
  const receiver = await startReceiver({ tls: { cert: readFileSync(certificate), key: readFileSync(key) },
    onReport: report => writeFileSync(reportPath, JSON.stringify(report)) });
  process.once('SIGTERM', () => receiver.close().then(() => process.exit(0)));
  process.once('SIGINT', () => receiver.close().then(() => process.exit(0)));
}
