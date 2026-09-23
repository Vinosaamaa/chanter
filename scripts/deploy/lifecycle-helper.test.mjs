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
  let requests = 0, operation='checkpoint-get';
  const bytes=Buffer.alloc(10*1024*1024,0x80);
  const server = http.createServer((request, response) => {
    requests++;
    const expected=operation==='checkpoint-get' ? '/api/v1/internal/lifecycle/journal/checkpoint'
      : '/api/v1/internal/resource-recovery/objects/'+operation.slice(7);
    assert.equal(request.method, operation==='checkpoint-get' ? 'GET' : 'POST');
    assert.equal(request.url, expected);
    response.setHeader('Content-Type', 'application/json');
    if (request.headers[header] !== 'synthetic-private-helper-token') response.writeHead(401).end('{}');
    else if(operation==='object-read') {request.resume();response.setHeader('Content-Type','application/octet-stream');response.end(bytes);}
    else if(operation==='object-put') {
      assert.equal(request.headers['content-type'],'application/octet-stream');
      let total=0;request.on('data',chunk=>{total+=chunk.length;});
      request.on('end',()=>{assert.equal(total,bytes.length+6);response.end('{"accepted":true}');});
    } else response.end('{"checkpoint":null}');
  });
  // The shipped helper has no configurable address. This short-lived test owns only its fixed loopback listener.
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(8080, '127.0.0.1', resolve); });
  try {
    const invoke=input=>new Promise((resolve,reject)=>{
      const child=execFile(tool('java'), [operation==='checkpoint-get' ? '-Xmx32m' : '-Xmx64m','-XX:+UseSerialGC','-cp',output,'Lifecycle',operation], {
        timeout:25000,maxBuffer:bytes.length+4096,encoding:'buffer',windowsHide:true,
        env:{...process.env,CHANTER_INTERNAL_SERVICE_TOKEN:'synthetic-private-helper-token'},
      },(error,stdout)=>error ? reject(new Error(`Helper rejected fixed ${operation} operation (${error.code})`)) : resolve(stdout));
      child.stdin.on('error',reject);child.stdin.end(input);
    });
    const result=await invoke();
    assert.deepEqual(JSON.parse(result), { checkpoint: null });
    operation='object-read';assert.deepEqual(await invoke('{}'),bytes);
    operation='object-put';const prefix=Buffer.from([0,0,0,2,123,125]);
    assert.deepEqual(JSON.parse(await invoke(Buffer.concat([prefix,bytes]))),{accepted:true});
    assert.equal(requests, 3);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
