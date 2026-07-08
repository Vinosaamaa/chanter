# Issue #31 — change log

## Scope

Discord-like Friends Hub with live direct messages for Workable Product (#60 epic).

## Backend

| Area | Change |
|------|--------|
| `community-service` | `GET /api/v1/users/{peerUserId}/co-membership` — shared Study Server check (owners, instructors, enrolled learners) |
| `message-service` | `GET /api/v1/friendships`, auth principal on social APIs, co-membership gate on friend requests, `sentAt` on DM responses |
| `realtime-service` | Social WebSocket plane: `social_subscribed`, `presence_changed`, `send_dm`, `dm_message`; Redis-backed presence (`!test`) |
| `gateway-service` | Route co-membership API to community-service |
| `infra/docker-compose.yml` | `REDIS_HOST` for product realtime-service |

## Frontend

| Area | Change |
|------|--------|
| `features/friends/` | Friends list, DM panel, REST + social realtime client |
| `router.tsx` | `/app/friends` uses dedicated `FriendsHubLayout` (no 4-column study shell) |
| Call button | Disabled stub until **#32** |

## Usage

1. `make product-up` (or run stack locally)
2. Sign in and open **Friends** in the top bar → `/app/friends`
3. Two users who share a Study Server accept a friend request (REST or future inbox UI)
4. Select a friend → DM history loads; new messages fan out over WebSocket

## Tests

```bash
cd backend && mvn -pl community-service test -Dtest=SocialMembershipSmokeTest
cd backend && mvn -pl message-service test -Dtest=FriendRequestAndDirectMessageSmokeTest
cd backend && mvn -pl realtime-service test -Dtest=SocialRealtimeWebSocketSmokeTest,RealtimeWebSocketSmokeTest
cd frontend && npm test -- --run src/features/friends
```

## Local browser verification (2026-07-08)

1. Started gateway, auth, community, message, realtime on host; frontend at `http://127.0.0.1:5173`.
2. API: co-membership `true` → friend request → accept → `GET /friendships` → `POST /direct-messages`.
3. Browser: signed in as **Owner Alice** → `/app/friends` → friend **fb691068** in sidebar → DM **"Hello from browser test"** in conversation panel; disabled **Call** stub visible.

## PR review

Use **cubic Dev AI** — log fixes in `docs/operations/issue-31-cubic-fix.md`.

## Deferred

- Friend display names from auth profile lookup (MVP shows short user id)
- `/dev/demo` social panel API updates (product UX is `/app/friends`)
- DM voice — **#32**
- Two-browser live presence + inbound DM without refresh — **#63**
