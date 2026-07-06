# Issue #61 — change log

## Scope

Voice Channel WebRTC + LiveKit local stack for Workable Product (#60 epic).

## Backend

| Area | Change |
|------|--------|
| `community-service` | `LiveKitTokenIssuer`, `POST .../media-token` on voice channels and office hours |
| `pom.xml` | `io.livekit:livekit-server` SDK |
| `application.yml` | `chanter.livekit.*` configuration |
| Tests | `VoiceChannelMediaTokenSmokeTest` |

## Frontend

| Area | Change |
|------|--------|
| `features/voice/` | API client, LiveKit room hook, voice channel panel |
| `ChannelConversation.tsx` | Routes study `VOICE` channels to voice UI |
| `OfficeHoursPanel.tsx` | Join/mute/leave voice during active sessions |
| `package.json` | `livekit-client` |

## Usage

1. `make product-up` (LiveKit on `ws://localhost:7880`)
2. Open a study server `> study-room` voice channel
3. Click **Join voice** — browser connects via LiveKit SFU
4. Office Hours: join voice from the support operations panel when a session is live

## Tests

```bash
cd backend && mvn -pl community-service test -Dtest=VoiceChannelMediaTokenSmokeTest
cd frontend && npm test -- --run src/features/voice
```

## Deferred

- Automated two-browser audio E2E — **#63**
- DM voice — **#32**
