import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const run = promisify(execFile);
test('packaged lifecycle helper sends the owning internal header on its fixed private route', async () => {
  const output = path.resolve('.cache/lifecycle-helper-test');
  fs.mkdirSync(output, { recursive: true });
  const headers = 'backend/common/src/main/java/com/chanter/common/auth/AuthHeaders.java';
  const header = fs.readFileSync(headers, 'utf8').match(/INTERNAL_SERVICE_TOKEN = "([^"]+)"/)[1].toLowerCase();
  const tool = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
  await run(tool('javac'), ['-d', output, headers, 'infra/production/java/Lifecycle.java']);
  let requests = 0;
  const server = http.createServer((request, response) => {
    requests++;
    assert.equal(request.method, 'GET');
    assert.equal(request.url, '/api/v1/internal/lifecycle/journal/checkpoint');
    response.setHeader('Content-Type', 'application/json');
    if (request.headers[header] !== 'synthetic-private-helper-token') response.writeHead(401).end('{}');
    else response.end('{"checkpoint":null}');
  });
  // The shipped helper has no configurable address. This short-lived test owns only its fixed loopback listener.
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(8080, '127.0.0.1', resolve); });
  try {
    const child = execFile(tool('java'), ['-Xmx32m', '-cp', output, 'Lifecycle', 'checkpoint-get'], {
      timeout: 25000, maxBuffer: 1024, windowsHide: true,
      env: { ...process.env, CHANTER_INTERNAL_SERVICE_TOKEN: 'synthetic-private-helper-token' },
    });
    child.stdin.end();
    const result = await new Promise((resolve, reject) => {
      let stdout = '', stderr = '';
      child.stdout.on('data', value => { stdout += value; }); child.stderr.on('data', value => { stderr += value; });
      child.on('error', reject); child.on('close', code => code === 0 ? resolve(stdout) : reject(new Error(`Helper rejected owning route: ${code}; ${stderr}`)));
    });
    assert.deepEqual(JSON.parse(result), { checkpoint: null });
    assert.equal(requests, 1);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
