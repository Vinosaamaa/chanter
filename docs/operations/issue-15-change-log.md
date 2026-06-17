# Issue 15 Change Log: Send Friend Request And Direct Message

Date: 2026-06-17  
Branch: `feature/15-send-friend-request-and-dm`  
Issue: `#15 Slice: Send Friend Request And Direct Message`  
Commit status: in progress on feature branch.

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

## Verification

- `JAVA_HOME=... mvn -pl message-service -Dtest=FriendRequestAndDirectMessageSmokeTest test`
- `JAVA_HOME=... mvn verify`

## Follow-Up Notes

- Frontend manual Friend Request / DM panel is not yet added in this commit.
- Real caller identity remains deferred to issue #30.
