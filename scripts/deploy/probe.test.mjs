import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const run = promisify(execFile);
test('the actual container health helper rejects unhealthy and missing endpoints', async () => {
  const output = path.resolve('.cache/probe-test');
  fs.mkdirSync(output, { recursive: true });
  const tool = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
  await run(tool('javac'), ['-d', output, 'infra/production/java/Probe.java']);
  const server = http.createServer((req, res) => {
    if (req.url === '/actuator/health/readiness') res.end('{"status":"UP"}');
    else if (req.url === '/actuator/health/down') res.end('{"status":"DOWN"}');
    else { res.statusCode = 503; res.end('unavailable'); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    const probe = suffix => run(tool('java'), ['-cp', output, 'Probe', base + suffix]);
    await assert.doesNotReject(probe('/actuator/health/readiness'));
    await assert.rejects(probe('/actuator/health/down'), { code: 1 });
    await assert.rejects(probe('/missing'), { code: 1 });
  } finally { await new Promise(resolve => server.close(resolve)); }
});
