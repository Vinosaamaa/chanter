#!/usr/bin/env bash
set -euo pipefail
mkdir -p .cache/vector-proof
mkdir -p .cache/vector-proof/classes/com/chanter/agent/infra
cp backend/agent-service/target/test-classes/com/chanter/agent/infra/VectorRuntimeProbe.class .cache/vector-proof/classes/com/chanter/agent/infra/
node scripts/vector/download-model.mjs backend/agent-service/target/embedding-model
locked() { node -e 'process.stdout.write(JSON.parse(require("fs").readFileSync("infra/production/runtime-lock.json"))[process.argv[1]])' "$1"; }
docker build -f infra/production/java/Dockerfile --build-arg MODULE=agent-service \
  --build-arg "JDK_IMAGE=$(locked jdk)" --build-arg "JRE_IMAGE=$(locked jre)" -t chanter-vector-runtime .
docker run --name chanter-vector-runtime --network host --memory 640m --memory-swap 640m --cpus 2 --cpuset-cpus 0,1 \
  --read-only --cap-drop ALL --security-opt no-new-privileges:true --user 10001:10001 \
  --tmpfs /tmp:size=64m,mode=1777 \
  --mount "type=bind,src=$PWD/.cache/vector-proof/classes,dst=/probe,readonly" \
  -e 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=10 -XX:MaxDirectMemorySize=64m -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError -Xss512k' \
  -e CHANTER_JWT_SECRET=vector-runtime-test-jwt-secret-32bytes \
  -e CHANTER_INTERNAL_SERVICE_TOKEN=vector-runtime-test-internal-token-32bytes \
  -e CHANTER_EMBEDDINGS_PROVIDER=onnx -e CHANTER_EMBEDDINGS_MODEL_DIRECTORY=/app/models/minilm \
  --entrypoint java chanter-vector-runtime -cp '/app/classes:/app/lib/*:/probe' com.chanter.agent.infra.VectorRuntimeProbe \
  --server.port=0 --spring.datasource.url='jdbc:postgresql://127.0.0.1:5547/vector_test?currentSchema=runtime_fixture' \
  --spring.datasource.username=vector_test --spring.datasource.password=vector-test-only-password \
  --spring.flyway.schemas=runtime_fixture --spring.flyway.default-schema=runtime_fixture \
  --chanter.events.dispatch-enabled=false --spring.datasource.hikari.maximum-pool-size=5 \
  --spring.datasource.hikari.minimum-idle=1 --server.tomcat.threads.max=40 \
  | tee .cache/vector-proof/runtime.log
docker cp chanter-vector-runtime:/tmp/vector-query-plan.json .cache/vector-proof/query-plan.json
test "$(docker inspect -f '{{.State.OOMKilled}}' chanter-vector-runtime)" = false
grep '^VECTOR_PROOF ' .cache/vector-proof/runtime.log
