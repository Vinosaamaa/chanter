import { createServer } from 'node:http';
import { CompanionError } from './codex-app-server.mjs';
import { NativeRequestGate } from './native-request.mjs';

/** Internal listener, started only by a native launcher with a pinned deployment and approval UI. */
export async function startNativeServer({ state, origin, publicKey, approve, transport, port = 43160 }) {
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new CompanionError('INVALID_NATIVE_CONFIGURATION');
  let gate, host;
  const controllers = new Set();
  const server = createServer({ headersTimeout: 5000, requestTimeout: 5000, keepAliveTimeout: 1000 }, (req, res) => {
    void handle(req, res).catch(() => res.destroy());
  });
  server.maxConnections = 4;
  server.maxHeadersCount = 16;
  server.maxRequestsPerSocket = 8;
  await new Promise((resolve, reject) => {
    server.once('error', () => reject(new CompanionError('NATIVE_LISTENER_UNAVAILABLE')));
    server.listen(port, '127.0.0.1', resolve);
  });
  host = `127.0.0.1:${server.address().port}`;
  try { gate = new NativeRequestGate({ state, origin, host, publicKey, approve, transport }); }
  catch (error) { await new Promise((resolve) => server.close(resolve)); throw error; }

  async function handle(req, res) {
    const counts = new Map();
    for (let i = 0; i < req.rawHeaders.length; i += 2) {
      const name = req.rawHeaders[i].toLowerCase(); counts.set(name, (counts.get(name) ?? 0) + 1);
    }
    if (!gate || req.socket.remoteAddress !== '127.0.0.1' || req.headers.host !== host || req.headers.origin !== origin
        || ['host', 'origin', 'content-type', 'x-chanter-pairing'].some((name) => (counts.get(name) ?? 0) > 1)) {
      res.writeHead(403, { 'connection': 'close', 'cache-control': 'no-store' }); res.end(); return;
    }
    const cors = { 'access-control-allow-origin': origin, 'vary': 'Origin', 'cache-control': 'no-store' };
    if (req.method === 'OPTIONS' && req.url === '/study') {
      const headers = (req.headers['access-control-request-headers'] ?? '').toLowerCase().split(',').map((name) => name.trim()).sort();
      if (req.headers['access-control-request-method'] !== 'POST' || headers.join(',') !== 'content-type,x-chanter-pairing') {
        res.writeHead(403, cors); res.end(); return;
      }
      res.writeHead(204, { ...cors, 'access-control-allow-methods': 'POST',
        'access-control-allow-headers': 'Content-Type, X-Chanter-Pairing', 'access-control-allow-private-network': 'true' });
      res.end(); return;
    }
    if (req.method !== 'POST' || req.url !== '/study') { res.writeHead(404, cors); res.end(); return; }
    if (!/^[a-zA-Z0-9_-]{43}$/.test(req.headers['x-chanter-pairing'] ?? '')) {
      res.writeHead(401, { ...cors, 'connection': 'close' }); res.end(); return;
    }
    if (req.headers['content-type'] !== 'application/json') { res.writeHead(415, cors); res.end(); return; }
    const controller = new AbortController(); controllers.add(controller);
    const disconnect = () => { if (!res.writableEnded) controller.abort(); };
    req.once('aborted', disconnect); res.once('close', disconnect);
    let bytes = 0, outputBytes = 0, wireBytes = 0;
    const chunks = [];
    const bodyTimer = setTimeout(() => { controller.abort(); req.destroy(); }, 5000);
    function event(type, value) {
      if (controller.signal.aborted || res.destroyed) throw new CompanionError('STUDY_CANCELLED');
      const encoded = `event: ${type}\ndata: ${JSON.stringify(value)}\n\n`;
      wireBytes += Buffer.byteLength(encoded);
      if (wireBytes > 512 * 1024) throw new CompanionError('STUDY_OUTPUT_LIMIT');
      if (!res.headersSent) res.writeHead(200, { ...cors, 'content-type': 'text/event-stream', 'x-content-type-options': 'nosniff' });
      // Total framed output is bounded, including data queued for a slow loopback client.
      res.write(encoded);
    }
    try {
      for await (const chunk of req) {
        bytes += chunk.length;
        if (bytes > 144 * 1024) throw new CompanionError('INVALID_STUDY_REQUEST');
        chunks.push(chunk);
      }
      clearTimeout(bodyTimer);
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { throw new CompanionError('INVALID_STUDY_REQUEST'); }
      if (!body || Array.isArray(body) || Object.keys(body).sort().join(',') !== 'prompt,ticket') throw new CompanionError('INVALID_STUDY_REQUEST');
      const result = await gate.execute({ headers: req.headers, ...body }, { signal: controller.signal,
        onDelta: (delta) => {
          if (typeof delta !== 'string') throw new CompanionError('PROVIDER_PROTOCOL_ERROR');
          outputBytes += Buffer.byteLength(delta);
          if (outputBytes > 64 * 1024) throw new CompanionError('STUDY_OUTPUT_LIMIT');
          event('delta', { text: delta });
        } });
      event('completed', result); res.end();
    } catch (error) {
      controller.abort();
      const code = error instanceof CompanionError ? error.code : 'NATIVE_REQUEST_FAILED';
      if (!res.destroyed) {
        if (res.headersSent) res.end(`event: error\ndata: ${JSON.stringify({ error: code })}\n\n`);
        else { res.writeHead(400, { ...cors, 'content-type': 'application/json', 'connection': 'close' }); res.end(JSON.stringify({ error: code })); }
      }
    } finally {
      clearTimeout(bodyTimer); controllers.delete(controller);
      req.off('aborted', disconnect); res.off('close', disconnect);
    }
  }

  return {
    port: server.address().port,
    // Native UI calls this directly. It is deliberately absent from the HTTP routes.
    pair: (ticket) => gate.pair(ticket),
    async close() {
      for (const controller of controllers) controller.abort();
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
    },
  };
}
