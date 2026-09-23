import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { isDeepStrictEqual } from 'node:util';
import { environmentName, exactFields, nonzeroUuid, validateWatermark } from './terminal-journal.mjs';
import { inventorySnapshot, inventoryObject, MAX_INVENTORY_REFERENCES } from './resource-inventory.mjs';

const MAX_BYTES=10*1024*1024, MAX_JSON=256*1024;
const fail=()=>{throw new Error('Private resource recovery transport rejected');};
function identity(value,fields) {
  exactFields(value,fields);nonzeroUuid(value.inventoryId);
  if(fields.includes('databaseBackupId'))nonzeroUuid(value.databaseBackupId);
  if(fields.includes('authority'))validateWatermark(value.authority);
  if(fields.includes('resourceId'))nonzeroUuid(value.resourceId);
  if(fields.includes('ordinal')&&(!Number.isSafeInteger(value.ordinal)||value.ordinal<1||value.ordinal>MAX_INVENTORY_REFERENCES))fail();
}
const requestFields=['inventoryId','databaseBackupId','authority','ordinal'];

/** Fixed owning-container transport. It does not establish provider closure or authorize public access. */
export function resourceRecoveryClient({environment,composeFile,project=`chanter-${environment}`,execute=execFileSync}) {
  environmentName(environment);
  if(!path.isAbsolute(composeFile)||!/^(?:chanter-(?:staging|production)|chanter-recovery-[a-f0-9]{32})$/.test(project))fail();
  const invoke=(operation,value,bytes)=>{
    const metadata=Buffer.from(JSON.stringify(value));if(metadata.length>4096)fail();
    let input=metadata;
    if(bytes!==undefined) {
      if(!Buffer.isBuffer(bytes)||bytes.length<1||bytes.length>MAX_BYTES)fail();
      const length=Buffer.alloc(4);length.writeUInt32BE(metadata.length);input=Buffer.concat([length,metadata,bytes]);
    }
    let output;
    try {output=execute('docker',['compose','--project-name',project,'-f',composeFile,'exec','-T','media-service',
      'java','-Xms8m',operation==='object-read'||operation==='object-put'?'-Xmx64m':'-Xmx32m','-XX:MaxMetaspaceSize=48m',
      '-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-cp','/app/helpers','Lifecycle',operation],
    {input,encoding:'buffer',timeout:30000,maxBuffer:operation==='object-read'?MAX_BYTES:MAX_JSON,
      stdio:['pipe','pipe','pipe'],windowsHide:true});}catch{fail();}
    if(!Buffer.isBuffer(output)||output.length>(operation==='object-read'?MAX_BYTES:MAX_JSON))fail();
    if(operation==='object-read'){if(output.length<1)fail();return output;}
    try{return JSON.parse(output.toString('utf8'));}catch{fail();}
  };
  const mutate=(operation,request,bytes)=>{
    identity(request,requestFields);
    const result=invoke(`object-${operation.toLowerCase()}`,request,bytes);
    exactFields(result,['schemaVersion','operation','request']);
    if(result.schemaVersion!==1||result.operation!==operation||!isDeepStrictEqual(result.request,request))fail();
    return result;
  };
  return Object.freeze({
    kind:'remote',
    fence:inventoryId=>{
      nonzeroUuid(inventoryId);const result=invoke('inventory-fence',{inventoryId});
      exactFields(result,['inventoryId','startedAt','storageNamespaceSha256','unsettledMutations']);
      if(result.inventoryId!==inventoryId||typeof result.startedAt!=='string'||!Number.isFinite(Date.parse(result.startedAt))
        ||result.storageNamespaceSha256!==null&&!/^[a-f0-9]{64}$/.test(result.storageNamespaceSha256)
        ||!Number.isSafeInteger(result.unsettledMutations)||result.unsettledMutations<0||result.unsettledMutations>4096)fail();
      return result;
    },
    capture:request=>{
      identity(request,['inventoryId','databaseBackupId','authority']);
      const result=inventorySnapshot(invoke('inventory-capture',request));
      if(result.inventoryId!==request.inventoryId||result.databaseBackupId!==request.databaseBackupId
        ||!isDeepStrictEqual(result.authority,request.authority))fail();
      return result;
    },
    page:request=>{
      identity(request,['inventoryId','authority','after','limit']);
      if(!Number.isSafeInteger(request.after)||request.after<0||request.after>MAX_INVENTORY_REFERENCES
        ||!Number.isSafeInteger(request.limit)||request.limit<1||request.limit>256)fail();
      const result=invoke('inventory-page',request);exactFields(result,['schemaVersion','snapshot','after','references','nextAfter']);
      const snapshot=inventorySnapshot(result.snapshot);
      if(result.schemaVersion!==1||result.after!==request.after||snapshot.inventoryId!==request.inventoryId
        ||!isDeepStrictEqual(snapshot.authority,request.authority)||!Array.isArray(result.references)||result.references.length>request.limit)fail();
      // The enclosing InventoryVerifier validates complete page order, tuple chain and final count.
      return result;
    },
    discard:inventoryId=>{
      nonzeroUuid(inventoryId);const result=invoke('inventory-discard',{inventoryId});
      exactFields(result,['schemaVersion','inventoryId','snapshotDiscarded','maintenanceReleased']);
      if(result.schemaVersion!==1||result.inventoryId!==inventoryId||result.snapshotDiscarded!==true||result.maintenanceReleased!==false)fail();return result;
    },
    read:request=>{identity(request,requestFields);return invoke('object-read',request);},
    put:(request,bytes)=>mutate('PUT',request,bytes),
    delete:request=>mutate('DELETE',request),
    finish:request=>{
      identity(request,['inventoryId','databaseBackupId','authority','resourceId']);
      const result=invoke('object-finish-delete',request);exactFields(result,['schemaVersion','request','sourceAccountingCommitted']);
      if(result.schemaVersion!==1||!isDeepStrictEqual(result.request,request)||result.sourceAccountingCommitted!==true)fail();return result;
    },
  });
}

/** The caller must already possess verified writer/provider closure. A local fence never creates that proof. */
export function captureResourceInventory(client,archive,{databaseBackupId,maintenance}) {
  nonzeroUuid(databaseBackupId);
  const proof=structuredClone(maintenance);
  exactFields(proof,['inventoryId','storageNamespaceSha256','writers','unsettledWrites','authority']);
  nonzeroUuid(proof.inventoryId);validateWatermark(proof.authority);
  if(proof.writers!=='QUIESCENT'||proof.unsettledWrites!==0||!/^[a-f0-9]{64}$/.test(proof.storageNamespaceSha256??''))fail();
  const fence=client.fence(proof.inventoryId);
  if(fence.inventoryId!==proof.inventoryId||fence.storageNamespaceSha256!==proof.storageNamespaceSha256||fence.unsettledMutations!==0)fail();
  const snapshot=client.capture({inventoryId:proof.inventoryId,databaseBackupId,authority:proof.authority});
  return archive.publishInventory(snapshot,proof,
    (after,limit)=>client.page({inventoryId:proof.inventoryId,authority:proof.authority,after,limit}),
    reference=>archive.capture(inventoryObject(reference),proof,client.read({inventoryId:proof.inventoryId,databaseBackupId,
      authority:proof.authority,ordinal:reference.ordinal})));
}
