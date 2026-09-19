#!/usr/bin/env bash
set -euo pipefail
mkdir -p .cache/vector-proof
mkdir -p .cache/vector-proof/classes/com/chanter/agent/infra
cp backend/agent-service/target/test-classes/com/chanter/agent/infra/VectorRuntimeProbe.class .cache/vector-proof/classes/com/chanter/agent/infra/
docker exec chanter-vector-test psql -U vector_test -d vector_test -v ON_ERROR_STOP=1 \
  -c "CREATE ROLE vector_agent LOGIN PASSWORD 'vector-agent-test-password' NOSUPERUSER NOCREATEDB NOCREATEROLE" \
  -c 'CREATE SCHEMA runtime_fixture AUTHORIZATION vector_agent'
node scripts/vector/download-model.mjs backend/agent-service/target/embedding-model
locked() { node -e 'process.stdout.write(JSON.parse(require("fs").readFileSync("infra/production/runtime-lock.json"))[process.argv[1]])' "$1"; }
docker build -f infra/production/java/Dockerfile --build-arg MODULE=agent-service \
  --build-arg MODEL_ASSET_SOURCE=backend/agent-service/target/embedding-model \
  --build-arg "JDK_IMAGE=$(locked jdk)" --build-arg "JRE_IMAGE=$(locked jre)" -t chanter-vector-runtime .
mkdir -p .cache/vector-telemetry
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -keyout .cache/vector-telemetry/key.pem -out .cache/vector-telemetry/cert.pem \
  -subj '/CN=localhost' -addext 'subjectAltName=IP:127.0.0.1' > .cache/vector-telemetry/certificate.log 2>&1
VECTOR_TEST_TLS_DIR="$PWD/.cache/vector-telemetry" node --test scripts/vector/telemetry-receiver.test.mjs
rm -f .cache/vector-proof/telemetry.json
node scripts/vector/telemetry-receiver.mjs .cache/vector-proof/telemetry.json \
  .cache/vector-telemetry/cert.pem .cache/vector-telemetry/key.pem &
receiver_pid=$!
trap 'kill "$receiver_pid" 2>/dev/null || true; wait "$receiver_pid" 2>/dev/null || true' EXIT
for attempt in $(seq 1 50); do
  if test -s .cache/vector-proof/telemetry.json; then break; fi
  kill -0 "$receiver_pid"
  sleep 0.1
done
node --input-type=module <<'NODE'
import { readFileSync, writeFileSync } from 'node:fs';
import { telemetryEnvironment } from './scripts/deploy/telemetry.mjs';
const { port } = JSON.parse(readFileSync('.cache/vector-proof/telemetry.json'));
if (!Number.isInteger(port) || port < 1) throw Error('Fixture receiver did not bind');
const environment = telemetryEnvironment({ CHANTER_TELEMETRY_ENDPOINT: `https://127.0.0.1:${port}/v1/traces`,
  CHANTER_TELEMETRY_AUTHORIZATION: 'Bearer vector-test-only' });
writeFileSync('.cache/vector-telemetry/telemetry.env', Object.entries(environment).map(([key, value]) => `${key}=${value}`).join('\n') + '\n');
NODE
release_commit=$(git rev-parse HEAD)
docker run --name chanter-vector-runtime --network host --memory 640m --memory-swap 640m --cpus 2 --cpuset-cpus 0,1 \
  --read-only --cap-drop ALL --security-opt no-new-privileges:true --user 10001:10001 \
  --tmpfs /tmp:size=64m,mode=1777 \
  --mount "type=bind,src=$PWD/.cache/vector-proof/classes,dst=/probe,readonly" \
  --mount "type=bind,src=$PWD/.cache/vector-telemetry/cert.pem,dst=/probe-certificate.pem,readonly" \
  --env-file .cache/vector-telemetry/telemetry.env \
  -e OTEL_EXPORTER_OTLP_CERTIFICATE=/probe-certificate.pem -e OTEL_SERVICE_NAME=agent-service \
  -e "OTEL_RESOURCE_ATTRIBUTES=service.version=$release_commit,deployment.environment.name=test,private=vector-private-canary-247" \
  -e CHANTER_ENVIRONMENT=test -e "CHANTER_RELEASE=$release_commit" \
  -e LOGGING_STRUCTURED_FORMAT_CONSOLE=com.chanter.common.telemetry.SafeLogFormatter \
  -e 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=10 -XX:MaxDirectMemorySize=64m -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError -Xss512k' \
  -e CHANTER_JWT_SECRET=vector-runtime-test-jwt-secret-32bytes \
  -e CHANTER_INTERNAL_SERVICE_TOKEN=vector-runtime-test-internal-token-32bytes \
  -e CHANTER_EMBEDDINGS_PROVIDER=onnx -e CHANTER_EMBEDDINGS_MODEL_DIRECTORY=/app/models/minilm \
  --entrypoint java chanter-vector-runtime -javaagent:/app/telemetry/agent.jar -cp '/app/classes:/app/lib/*:/probe' com.chanter.agent.infra.VectorRuntimeProbe \
  --server.port=0 --spring.datasource.url='jdbc:postgresql://127.0.0.1:5547/vector_test?currentSchema=runtime_fixture' \
  --spring.datasource.username=vector_agent --spring.datasource.password=vector-agent-test-password \
  --spring.flyway.schemas=runtime_fixture --spring.flyway.default-schema=runtime_fixture \
  --chanter.events.dispatch-enabled=false --spring.datasource.hikari.maximum-pool-size=5 \
  --spring.datasource.hikari.minimum-idle=1 --server.tomcat.threads.max=40 \
  | tee .cache/vector-proof/runtime.log
node -e 'const fs=require("fs"); const line=fs.readFileSync(".cache/vector-proof/runtime.log","utf8").split("\n").find(line=>line.startsWith("VECTOR_QUERY_PLAN ")); if(!line) throw Error("Missing actual scoped query plan"); fs.writeFileSync(".cache/vector-proof/query-plan.json",JSON.stringify(JSON.parse(line.slice(18)),null,2));'
test "$(docker inspect -f '{{.State.OOMKilled}}' chanter-vector-runtime)" = false
grep '^VECTOR_PROOF ' .cache/vector-proof/runtime.log
node --input-type=module <<'NODE'
import { readFileSync } from 'node:fs';
const report = JSON.parse(readFileSync('.cache/vector-proof/telemetry.json'));
if (report.traces < 1 || report.metrics < 1 || !report.httpObserved || !report.jvmObserved || report.canaryDetected || report.rejected) {
  throw Error(`Successful private trace/metric export proof failed: ${JSON.stringify(report)}`);
}
console.log('VECTOR_TELEMETRY ' + JSON.stringify(report));
NODE
