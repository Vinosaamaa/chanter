import test from 'node:test';
import assert from 'node:assert/strict';
import { gzipSync } from 'node:zlib';
import { CANARY, startReceiver } from './telemetry-receiver.mjs';

test('receiver requires successful trace and metric bodies and persists no payload', async () => {
  const reports = [];
  const receiver = await startReceiver({ onReport: report => reports.push(report) });
  try {
    const base = `http://127.0.0.1:${receiver.snapshot().port}`;
    const post = (path, body, extra = {}) => fetch(base + path, { method: 'POST', body,
      headers: { 'Content-Type': 'application/x-protobuf', ...extra } });
    assert.equal((await post('/v1/traces', Buffer.from('agent-service\0HTTP GET'))).status, 200);
    assert.equal((await post('/v1/metrics', gzipSync(Buffer.from('agent-service\0jvm.memory.used')),
      { 'Content-Encoding': 'gzip' })).status, 200);
    const report = receiver.snapshot();
    assert.equal(report.traces, 1); assert.equal(report.metrics, 1); assert.equal(report.rejected, 0);
    assert.equal(report.httpObserved, true); assert.equal(report.jvmObserved, true);
    assert.equal(JSON.stringify(reports).includes('HTTP GET'), false);
    assert.equal((await post('/v1/traces', Buffer.from(`agent-service\0HTTP GET\0${CANARY}`))).status, 400);
    assert.equal(receiver.snapshot().canaryDetected, true);
    assert.equal(receiver.snapshot().traces, 1);
  } finally { await receiver.close(); }
});

test('receiver rejects empty, malformed and decompressed oversized exports', async () => {
  const receiver = await startReceiver();
  try {
    const url = `http://127.0.0.1:${receiver.snapshot().port}/v1/metrics`;
    for (const body of [Buffer.alloc(0), Buffer.from('not gzip'), gzipSync(Buffer.alloc(1024 * 1024 + 1))]) {
      const response = await fetch(url, { method: 'POST', body,
        headers: { 'Content-Type': 'application/x-protobuf', 'Content-Encoding': 'gzip' } });
      assert.equal(response.status, 400);
    }
    assert.equal(receiver.snapshot().metrics, 0); assert.equal(receiver.snapshot().rejected, 3);
  } finally { await receiver.close(); }
});
