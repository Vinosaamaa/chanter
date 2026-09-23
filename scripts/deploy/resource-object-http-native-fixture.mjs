/** Hosted-only actual private HTTP transport and combined media/helper cgroup proof. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { resourceRecoveryClient } from './resource-recovery-client.mjs';

export function withResourceRecoveryHttp({definition,root,project}, operation) {
  assert.equal(process.env.GITHUB_ACTIONS,'true');
  assert.equal(process.env.CHANTER_SOURCE_RECOVERY_PREVIEW,'true');
  assert.match(project,/^(?:chanter-smoke-(?:amd64|arm64)-[a-z0-9-]+|chanter-recovery-[a-f0-9]{32})$/);
  const execute=(args,options={})=>{
    try{return execFileSync('docker',args,{encoding:'utf8',timeout:180_000,maxBuffer:1024*1024,
      stdio:['pipe','pipe','pipe'],...options});}catch{throw new Error('Hosted private object transport failed');}
  };
  const owned=structuredClone(definition),media=owned.services['media-service'];
  assert.equal(media.mem_limit,'512m');
  assert.equal(media.cpus,'2.0');
  Object.assign(media.environment,{CHANTER_SOURCE_RECOVERY_PREVIEW:'true',CHANTER_RECOVERY_MODE:'true',
    CHANTER_MEDIA_RECOVERY_INVENTORY_ENABLED:'true',CHANTER_ERRORS_ENABLED:'false',CHANTER_TELEMETRY_ENABLED:'false',
    OTEL_TRACES_EXPORTER:'none',OTEL_METRICS_EXPORTER:'none'});
  media.entrypoint=['java','-cp','/opt/canonical-fixture:/app/classes:/app/lib/*','ResourceRecoveryHttpFixture'];
  media.command=[];
  const file=path.join(root,`object-http-${crypto.randomUUID()}.json`);
  fs.writeFileSync(file,JSON.stringify(owned),{mode:0o600});
  const compose=args=>execute(['compose','--project-name',project,'-f',file,...args]);
  const clientProject=`chanter-recovery-${crypto.randomUUID().replaceAll('-','')}`;
  const client=resourceRecoveryClient({environment:'staging',composeFile:file,project:clientProject,
    execute:(command,args,options)=>{
      assert.equal(command,'docker');assert.equal(args[2],clientProject);assert.equal(args[4],file);
      const translated=[...args];translated[2]=project;return execute(translated,options);
    }});
  let id;
  try {
    assert.equal(compose(['ps','--status','running','--quiet','media-service']).trim(),'');
    compose(['up','-d','--no-deps','--wait','--wait-timeout','180','media-service']);
    id=compose(['ps','--quiet','media-service']).trim();assert.match(id,/^[a-f0-9]{64}$/);
    const state=JSON.parse(execute(['inspect',id]))[0];
    assert.equal(state.Config.Labels['com.docker.compose.project'],project);
    assert.equal(state.Config.Labels['com.docker.compose.service'],'media-service');
    assert.equal(state.HostConfig.Memory,512*1024*1024);
    const result=operation(client);
    const peak=Number(execute(['exec',id,'cat','/sys/fs/cgroup/memory.peak']).trim());
    const events=Object.fromEntries(execute(['exec',id,'cat','/sys/fs/cgroup/memory.events']).trim().split('\n').map(line=>{
      const [key,value]=line.split(' ');return [key,Number(value)];
    }));
    assert.ok(Number.isSafeInteger(peak)&&peak>0&&peak<=512*1024*1024);
    assert.equal(events.oom,0);assert.equal(events.oom_kill,0);
    const final=JSON.parse(execute(['inspect',id]))[0];
    assert.equal(final.State.Running,true);assert.equal(final.State.OOMKilled,false);assert.equal(final.RestartCount,0);
    return {result,memory:{limitBytes:512*1024*1024,peakBytes:peak,oom:events.oom,oomKill:events.oom_kill}};
  } finally {
    // Preserve volumes. This is the same exact CI-owned service, never a production operator.
    if(id) {
      const current=compose(['ps','--all','--quiet','media-service']).trim();assert.equal(current,id);
    }
    compose(['stop','media-service']);
  }
}
