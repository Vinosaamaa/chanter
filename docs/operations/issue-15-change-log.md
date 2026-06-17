# Issue 15 Change Log: Send Friend Request And Direct Message

Date: 2026-06-17  
Branch: `feature/15-send-friend-request-and-dm`  
Issue: `#15 Slice: Send Friend Request And Direct Message`  
Commit status: backend committed; frontend committed locally (not pushed).

## Acceptance Criteria Covered

- User can send and accept Friend Requests.
- Friends can send Direct Messages.
- Non-friends cannot DM without acceptance.
- Tests cover accept/decline and block behavior.

## 1. Bootstrapped Message Service Module

Files:

- `backend/message-service/pom.xml`
- `backend/message-service/src/main/java/com/chanter/message/MessageServiceApplication.java`
- `backend/pom.xml` (module registration)

What changed:

- Added `message-service` as a runnable Spring Boot microservice on port `8083` with Flyway, JDBC, and H2 test profile.

## 2. Added TDD Smoke Tests For Friend Requests And DMs

File:

- `backend/message-service/src/test/java/com/chanter/message/api/FriendRequestAndDirectMessageSmokeTest.java`

What changed:

- Added smoke tests for accepted Friend Request → DM exchange, declined request blocking DMs, and user block preventing DMs.

## 3. Added Social Messaging Backend Slice

Files:

- `backend/message-service/src/main/resources/db/migration/V1__create_friend_and_dm_tables.sql`
- `backend/message-service/src/main/java/com/chanter/message/api/SocialMessagingController.java`
- `backend/message-service/src/main/java/com/chanter/message/application/SocialMessagingService.java`
- `backend/message-service/src/main/java/com/chanter/message/infra/JdbcSocialMessagingRepository.java`

What changed:

- Added REST endpoints for Friend Requests, Direct Messages, and user blocks.
- Enforced friendship and block checks before sending DMs.
- Used request-body user ids with `TODO(#auth)` consistent with prior slices.

Representative service snippet:

```java
if (!repository.areFriends(senderUserId, recipientUserId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct Messages require an accepted Friend Request");
}
```

## 4. Routed Message Endpoints Through Gateway

Files:

- `backend/gateway-service/src/main/resources/application.yml`
- `.env.example`
- `Makefile` (`backend-message` target)

## 5. Added Frontend Friend Request And DM Panel

Files:

- `frontend/src/App.tsx`
- `frontend/src/App.css`

What changed:

- Added a platform-wide **Direct Messages / Friend Requests** panel in the left workspace sidebar.
- Wired buttons for send/accept/decline Friend Request, send/refresh Direct Messages, non-friend DM guard check, block user, and blocked-user DM guard check.
- Uses seeded `ownerUserId`, `learnerUserId`, and `nonEnrolledUserId` ids (same `TODO(#auth)` posture as prior slices).
- Status panel includes Message service health via gateway.

## Verification

- `JAVA_HOME=... mvn -pl message-service -Dtest=FriendRequestAndDirectMessageSmokeTest test`
- `JAVA_HOME=... mvn verify`
- `npm run lint` and `npm run build` (frontend)
- Local browser smoke test at `http://127.0.0.1:5173/` (2026-06-17):
  - Send Request → `PENDING`
  - Accept → `ACCEPTED`
  - Send DM → message appears in list (`Want to study together?`)
  - Refresh DMs → list retained
  - Check Non-Friend → `403`, UI shows blocked
  - Block Owner → success
  - Check Blocked DM → `403`, UI shows blocked

## Follow-Up Notes

- Real caller identity remains deferred to issue #30.
- PR not opened yet; awaiting owner review before push/merge.
