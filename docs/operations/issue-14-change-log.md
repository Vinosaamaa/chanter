# Issue 14 Change Log: Join A Voice Channel

Date: 2026-06-17  
Branch: `feature/14-join-voice-channel`  
Issue: `#14 Slice: Join A Voice Channel`  
Commit status: complete on feature branch; browser smoke test verified locally.

## Acceptance Criteria Covered

- Study Server has a default Voice Channel from the #12 Study Server creation slice.
- Study Server member can join the Voice Channel.
- Joined member receives visible presence with speak/listen capability flags.
- Non-member cannot join the Voice Channel.
- Member can leave the Voice Channel and visible presence is removed.
- Tests cover join permissions and presence updates.

## 1. Added TDD Smoke Test For Voice Presence

File:

- `backend/community-service/src/test/java/com/chanter/community/api/VoiceChannelPresenceSmokeTest.java`

What changed:

- Added a MockMvc smoke test that creates a Study Server, finds its default `VOICE` channel, joins as the Owner member, lists visible presence, verifies a non-member receives `403`, leaves, and confirms the presence list is empty.

Representative snippet:

```java
MvcResult joinResult = mockMvc.perform(post(
                "/api/v1/study-server-channels/{channelId}/voice-presences",
                voiceChannel.id()
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "memberUserId", ownerUserId.toString()
                ))))
        .andExpect(status().isCreated())
        .andReturn();
```

TDD red result:

- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service -Dtest=VoiceChannelPresenceSmokeTest test`
- Failed with `Status expected:<201> but was:<404>` because the voice-presence endpoint did not exist.

## 2. Added Voice Presence Backend Slice

Files:

- `backend/community-service/src/main/java/com/chanter/community/api/CreateVoicePresenceRequest.java`
- `backend/community-service/src/main/java/com/chanter/community/api/StudyServerChannelController.java`
- `backend/community-service/src/main/java/com/chanter/community/api/VoicePresenceListResponse.java`
- `backend/community-service/src/main/java/com/chanter/community/api/VoicePresenceResponse.java`
- `backend/community-service/src/main/java/com/chanter/community/application/StudyServerRepository.java`
- `backend/community-service/src/main/java/com/chanter/community/application/StudyServerService.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/StudyServerChannel.java`
- `backend/community-service/src/main/java/com/chanter/community/domain/VoicePresence.java`
- `backend/community-service/src/main/java/com/chanter/community/infra/JdbcStudyServerRepository.java`
- `backend/community-service/src/main/resources/db/migration/V3__create_voice_presence_tables.sql`

What changed:

- Added REST endpoints under `/api/v1/study-server-channels/{channelId}/voice-presences`.
- Added a `voice_channel_presences` table keyed by Voice Channel and member user.
- Treated a Study Server member as any user with a row in `study_server_roles`; the current vertical slice can therefore use the Study Server Owner as the first member.
- Checked channel existence before permission checks, rejected non-Voice Channels, and denied non-members.
- Kept audio transport out of Spring; this slice records presence and returns speak/listen capability flags only.

Representative snippet:

```java
public VoicePresence joinVoiceChannel(UUID channelId, UUID memberUserId) {
    StudyServerChannel channel = requireVoiceChannel(channelId);
    requireStudyServerMember(channel.studyServerId(), memberUserId);

    return repository.saveVoicePresence(channelId, memberUserId);
}
```

## 3. Routed New Voice Endpoint Through Gateway

File:

- `backend/gateway-service/src/main/resources/application.yml`

What changed:

- Added `/api/v1/study-server-channels/**` to the community-service gateway route.

Representative snippet:

```yaml
- Path=/api/v1/study-servers/**,/api/v1/study-server-channels/**,/api/v1/cohorts/**,/api/v1/course-channels/**
```

## 4. Added Frontend Manual Voice Flow

Files:

- `frontend/src/App.tsx`
- `frontend/src/App.css`

What changed:

- Added a Voice Channel panel once a Study Server exists.
- Added Join, Check Non-Member, and Leave controls.
- Renders visible presence rows with member id plus `Speak` and `Listen` capability flags.

Representative snippet:

```tsx
const response = await fetch(`/api/v1/study-server-channels/${selectedVoiceChannel.id}/voice-presences`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ memberUserId: ownerUserId }),
})
```

## Verification

- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service -Dtest=VoiceChannelPresenceSmokeTest test`
- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service test`
- `JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn verify`
- `npm run lint`
- `npm run build`
- Browser verification at `http://localhost:5173/`: created a Study Server, joined `study-room`, observed `Speak`/`Listen` presence, verified non-member join denial, and left the Voice Channel.

## Follow-Up Notes

- This slice implements presence and capability readiness, not real audio transport. A later LiveKit/WebRTC slice should issue short-lived media tokens after these Spring permission checks pass.
