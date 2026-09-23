import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { resourceRecoveryClient } from './resource-recovery-client.mjs';

const inventoryId='11111111-1111-4111-8111-111111111111', databaseBackupId='22222222-2222-4222-8222-222222222222';
const authority={revision:0,digest:'0'.repeat(64)}, request={inventoryId,databaseBackupId,authority,ordinal:1};
const configuration={environment:'staging',composeFile:path.resolve('.cache/object-fixture.json')};
test('object transport fixes the owning service and keeps raw bytes and metadata off argv',()=>{
  const calls=[], bytes=Buffer.from([0,255,128,1]);
  const client=resourceRecoveryClient({...configuration,execute:(exe,args,options)=>{
    calls.push({exe,args,options});const operation=args.at(-1);
    if(operation==='object-read')return bytes;
    return Buffer.from(JSON.stringify({schemaVersion:1,operation:operation==='object-put'?'PUT':'DELETE',request}));
  }});
  assert.deepEqual(client.read(request),bytes);client.put(request,bytes);client.delete(request);
  assert.ok(calls.every(call=>call.exe==='docker'&&call.args.includes('media-service')&&call.args.includes('Lifecycle')));
  assert.ok(calls.every(call=>!JSON.stringify(call.args).includes(inventoryId)));
  const framed=calls[1].options.input,length=framed.readUInt32BE();
  assert.deepEqual(JSON.parse(framed.subarray(4,4+length)),request);assert.deepEqual(framed.subarray(4+length),bytes);
  assert.ok(calls[0].args.includes('-Xmx64m'));assert.equal(calls[0].options.timeout,30000);
  assert.ok(calls.every(call=>call.options.encoding==='buffer'&&call.options.maxBuffer<=10*1024*1024));
});
test('invalid selectors and malformed or mismatched receipts refuse without leaking response content',()=>{
  let calls=0;const client=resourceRecoveryClient({...configuration,execute:()=>{calls++;return Buffer.from('{}');}});
  for(const invalid of [{...request,key:'caller-key'},{...request,ordinal:0},{...request,authority:{revision:1,digest:'bad'}}])
    assert.throws(()=>client.read(invalid));
  assert.throws(()=>client.put(request,Buffer.alloc(10*1024*1024+1)));assert.equal(calls,0);
  for(const execute of [()=>{throw Error('private-provider-canary');},()=>Buffer.from('private-response-canary'),
    ()=>Buffer.from(JSON.stringify({schemaVersion:1,operation:'DELETE',request:{...request,ordinal:2}}))]) {
    const candidate=resourceRecoveryClient({...configuration,execute});
    assert.throws(()=>candidate.delete(request),error=>!error.message.includes('canary'));
  }
  for(const project of ['arbitrary','chanter-production --privileged'])assert.throws(()=>resourceRecoveryClient({...configuration,project}));
});
test('metadata transport validates maintenance and snapshot identity independently of process success',()=>{
  const client=resourceRecoveryClient({...configuration,execute:()=>Buffer.from(JSON.stringify({inventoryId,startedAt:'2026-09-22T00:00:00Z',
    storageNamespaceSha256:null,unsettledMutations:2}))});
  assert.equal(client.fence(inventoryId).unsettledMutations,2);
  assert.throws(()=>client.capture({inventoryId,databaseBackupId,authority}));
});
