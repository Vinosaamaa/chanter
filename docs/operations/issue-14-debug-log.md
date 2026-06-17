# Issue 14 Debug Log: Voice Channel Local Verification

Date: 2026-06-17  
Branch: `feature/14-join-voice-channel`  
Issue: `#14 Slice: Join A Voice Channel`

## Incident 1: Maven Module Command From Wrong Directory

Symptom:

- Running the first RED test command from the repository root failed before test execution.

Command:

```bash
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service -Dtest=VoiceChannelPresenceSmokeTest test
```

Finding:

- The Maven reactor root is `backend/`, not the repository root.

Fix:

- Re-ran the command from `/Users/wenkxu/Projects/Chanter/backend`.

## Incident 2: Parallel Dev Server Startup Corrupted Local Maven Metadata

Symptom:

- Starting auth, community, and gateway with `make backend-*` in parallel produced Maven install errors:

```text
Failed to install metadata com.chanter:common:0.1.0-SNAPSHOT/maven-metadata.xml
Could not read metadata .../maven-metadata-local.xml: input contained no data
```

Hypothesis:

- Multiple parallel `mvn install -DskipTests` executions wrote to the same local `~/.m2` metadata files at the same time.

Fix:

- Removed the empty local Maven metadata files under `~/.m2/repository/com/chanter/common`.
- Ran a single serial backend install:

```bash
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -B -q install -DskipTests
```

Result:

- Local Maven artifacts were repaired and services could be started directly with `mvn -pl <service> spring-boot:run`.

## Incident 3: Backend Port Binding Needed Unsandboxed Dev Server Runs

Symptom:

- Auth and gateway failed to bind local ports from sandboxed runs.
- Auth showed `java.net.SocketException: Operation not permitted`.
- Gateway showed `Port 8080 was already in use` during a transient retry, then no listener remained after the failed process exited.

Fix:

- Started auth and gateway outside the sandbox:

```bash
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -B -q -pl auth-service spring-boot:run
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -B -q -pl gateway-service spring-boot:run
```

Final verification:

- Gateway health returned `200`.
- Auth health through gateway returned `200`.
- Missing Study Server probe through gateway returned expected `404`.
- Browser flow passed for create Study Server, join Voice Channel, non-member denial, and leave Voice Channel.
