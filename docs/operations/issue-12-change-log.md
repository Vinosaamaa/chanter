# Issue 12 Change Log: Create A Study Server

Date: 2026-06-17  
Branch: `feature/12-create-study-server`  
Issue: `#12 Slice: Create A Study Server`  
Commit status: not committed yet at the time this log was created.

## Acceptance Criteria Covered

- User can create a Study Server from the frontend.
- Backend persists the Study Server and Owner role.
- Default Study Server Channels are created.
- Smoke test verifies create-and-view path.

## 1. Added Community Service Module

Files:

- `backend/pom.xml`
- `backend/community-service/pom.xml`
- `backend/community-service/src/main/java/com/chanter/community/CommunityServiceApplication.java`

What changed:

- Registered `community-service` as a Maven module.
- Added Spring Web, Validation, JDBC, Actuator, Flyway, PostgreSQL runtime, and H2 test dependencies.
- Added the Spring Boot entry point for Community Service.

Snippet:

```xml
<modules>
    <module>common</module>
    <module>gateway-service</module>
    <module>auth-service</module>
    <module>community-service</module>
</modules>
```

## 2. Added TDD Smoke Test For Create And View

File:

- `backend/community-service/src/test/java/com/chanter/community/api/StudyServerCreationSmokeTest.java`

What changed:

- Added a Spring MVC smoke test that creates a Study Server through `POST /api/v1/study-servers`.
- Reads the returned `Location` through `GET`.
- Verifies the persisted Owner role and default channels through the public HTTP API.

Snippet:

```java
MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Java Spring Study Group",
                        "ownerUserId", ownerUserId.toString()
                ))))
        .andExpect(status().isCreated())
        .andReturn();
```

Snippet:

```java
assertThat(created.ownerRole())
        .isEqualTo(new OwnerRoleResponse(ownerUserId, "STUDY_SERVER_OWNER"));
assertThat(created.channels())
        .extracting(ChannelResponse::name)
        .containsExactly("announcements", "general", "study-room");
assertThat(created.channels())
        .extracting(ChannelResponse::kind)
        .containsExactly("TEXT", "TEXT", "VOICE");
```

## 3. Added Study Server API

Files:

- `backend/community-service/src/main/java/com/chanter/community/api/CreateStudyServerRequest.java`
- `backend/community-service/src/main/java/com/chanter/community/api/StudyServerController.java`
- `backend/community-service/src/main/java/com/chanter/community/api/StudyServerResponse.java`

What changed:

- Added validated request body for Study Server creation.
- Added create and view endpoints.
- Returns `201 Created` with `Location` for created Study Servers.

Snippet:

```java
@PostMapping
public ResponseEntity<StudyServerResponse> createStudyServer(
        @Valid @RequestBody CreateStudyServerRequest request
) {
    StudyServer studyServer = studyServerService.createStudyServer(request.name(), request.ownerUserId());
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(studyServer.id())
            .toUri();

    return ResponseEntity.created(location).body(StudyServerResponse.from(studyServer));
}
```

## 4. Added Domain Model And Service Logic

Files:

- `backend/community-service/src/main/java/com/chanter/community/domain/StudyServer.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/OwnerRole.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/StudyServerChannel.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/StudyServerRole.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/ChannelKind.java`
- `backend/community-service/src/main/java/com/chanter/community/application/StudyServerService.java`
- `backend/community-service/src/main/java/com/chanter/community/application/StudyServerRepository.java`

What changed:

- Modeled Study Server, Owner role, channel kind, and Study Server Channel.
- Added application service that creates a Study Server and default channels.

Snippet:

```java
new StudyServer(
        UUID.randomUUID(),
        name.trim(),
        new OwnerRole(ownerUserId, StudyServerRole.STUDY_SERVER_OWNER),
        List.of(
                new StudyServerChannel(UUID.randomUUID(), "announcements", ChannelKind.TEXT, 0),
                new StudyServerChannel(UUID.randomUUID(), "general", ChannelKind.TEXT, 1),
                new StudyServerChannel(UUID.randomUUID(), "study-room", ChannelKind.VOICE, 2)
        ),
        clock.instant()
);
```

## 5. Added Persistence And Migration

Files:

- `backend/community-service/src/main/resources/db/migration/V1__create_study_server_tables.sql`
- `backend/community-service/src/main/java/com/chanter/community/infra/JdbcStudyServerRepository.java`
- `backend/community-service/src/main/resources/application.yml`
- `backend/community-service/src/test/resources/application-test.yml`

What changed:

- Added `study_servers`, `study_server_roles`, and `study_server_channels` tables.
- Configured PostgreSQL for local runtime and H2 for tests.
- Implemented JDBC save and read.
- Refactored read path so the returned Owner role comes from the role table.

Snippet:

```sql
CREATE TABLE study_server_roles (
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (study_server_id, user_id, role)
);
```

Snippet:

```java
private OwnerRole ownerRoleFor(UUID studyServerId) {
    return jdbcClient.sql("""
                    SELECT user_id, role
                    FROM study_server_roles
                    WHERE study_server_id = :studyServerId
                    AND role = :role
                    """)
            .param("studyServerId", studyServerId)
            .param("role", StudyServerRole.STUDY_SERVER_OWNER.name())
            .query((rs, rowNum) -> new OwnerRole(
                    rs.getObject("user_id", UUID.class),
                    StudyServerRole.valueOf(rs.getString("role"))
            ))
            .single();
}
```

## 6. Wired Gateway Route And Local CORS

File:

- `backend/gateway-service/src/main/resources/application.yml`

What changed:

- Added Gateway route for Community Service.
- Added local browser origins for both `localhost` and `127.0.0.1`.

Snippet:

```yaml
- id: community-service
  uri: ${COMMUNITY_SERVICE_URL:http://localhost:8082}
  predicates:
    - Path=/api/v1/study-servers/**
```

Snippet:

```yaml
allowedOrigins:
  - "http://localhost:5173"
  - "http://127.0.0.1:5173"
```

## 7. Added Frontend Create Flow And Study Server Shell

Files:

- `frontend/src/App.tsx`
- `frontend/src/App.css`
- `frontend/src/index.css`

What changed:

- Replaced health-only bootstrap page with a Study Server creation shell.
- Added form submission to `POST /api/v1/study-servers`.
- Added follow-up `GET /api/v1/study-servers/{id}` to verify create-and-view behavior.
- Renders default text and voice channels after creation.

Snippet:

```tsx
const response = await fetch('/api/v1/study-servers', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: serverName, ownerUserId }),
})
```

Snippet:

```tsx
const viewedResponse = await fetch(`/api/v1/study-servers/${created.id}`)

if (!viewedResponse.ok) {
  throw new Error(`View failed with ${viewedResponse.status}`)
}

setStudyServer(await viewedResponse.json())
```

## 8. Updated Local Commands And Docs

Files:

- `Makefile`
- `.env.example`
- `README.md`
- `backend/README.md`
- `backend/community-service/README.md`

What changed:

- Added `COMMUNITY_PORT` and `COMMUNITY_SERVICE_URL`.
- Added `make backend-community`.
- Updated local development docs to run Auth, Community, Gateway, and Frontend.

Snippet:

```makefile
backend-community:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl community-service spring-boot:run
```

## 9. Added Test Runtime Workaround For Local Sandbox

Files:

- `backend/auth-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `backend/community-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

What changed:

- Forced Mockito's subclass mock maker for tests.
- This avoids local sandbox failures where Mockito inline mock maker tries to self-attach a Byte Buddy agent.

Snippet:

```text
mock-maker-subclass
```

## Verification Commands

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -q verify
npm run lint
npm run build
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -q -pl community-service test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -q -pl gateway-service test
```

Browser verification:

- Opened `http://127.0.0.1:5173/`.
- Clicked `Create Study Server`.
- Confirmed the UI rendered the created Study Server shell with Owner role and default channels.
