# Issue 15 Change Log: Send Friend Request And Direct Message

Date: 2026-06-17  
Branch: `feature/15-send-friend-request-and-dm`  
Issue: `#15 Slice: Send Friend Request And Direct Message`  
Commit status: ready for PR review.

## Acceptance Criteria Covered

- User can send and accept Friend Requests.
- Friends can send Direct Messages.
- Non-friends cannot DM without acceptance.
- Tests cover accept/decline, block behavior, duplicate-request guard, and friendship removal.

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

- Added smoke tests for accepted Friend Request → DM exchange, declined request blocking DMs, user block preventing DMs, duplicate friend request rejection when already friends, and friendship removal blocking DMs until re-acceptance.

## 3. Added Social Messaging Backend Slice

Files:

- `backend/message-service/src/main/resources/db/migration/V1__create_friend_and_dm_tables.sql`
- `backend/message-service/src/main/java/com/chanter/message/api/SocialMessagingController.java`
- `backend/message-service/src/main/java/com/chanter/message/application/SocialMessagingService.java`
- `backend/message-service/src/main/java/com/chanter/message/infra/JdbcSocialMessagingRepository.java`

What changed:

- Added REST endpoints for Friend Requests, Direct Messages, user blocks, friendship status, and friendship removal.
- Enforced friendship and block checks before sending DMs.
- Rejected duplicate Friend Requests when users are already friends (`409`) or a request is already pending.
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

What changed:

- Gateway routes `/api/v1/friend-requests/**`, `/api/v1/friendships/**`, `/api/v1/direct-messages/**`, and `/api/v1/user-blocks/**` to `message-service`.

## 5. Added Frontend Friend Request And DM Demo Panel

Files:

- `frontend/src/App.tsx`
- `frontend/src/App.css`
- `frontend/src/index.css`

What changed:

- Added a scrollable **Friends & DMs** demo panel with explicit role labels (`Send as Owner`, `Accept as Learner`, etc.).
- Persisted demo user ids in `sessionStorage` so tab refresh does not break friendship state.
- Shows friendship status (`NONE` / `PENDING` / `ACCEPTED`), inbox, and demo-mode explainer copy.
- Disables send-request while friends or pending; adds **Remove friend** wired to `POST /api/v1/friendships/removal`.
- Surfaces social errors in the Friends panel (not only under Study Server create).
- Status panel includes Message service health via gateway.

## 6. Documented Post-MVP Social UX (#31–#32)

Files:

- `docs/architecture/social-hub-and-dm-voice.md`
- `docs/issues/education-mvp-issue-breakdown.md`
- `docs/product/education-mvp-prd.md`
- `CONTEXT.md`, `plan.md`, `System Design.md`, `HANDOFF.md`

What changed:

- Clarified that #15 ships durable REST + demo harness; Discord-like friends list, live DMs, and DM voice are tracked as GitHub issues **#31** and **#32**.

## Verification

- `JAVA_HOME=... mvn -pl message-service -Dtest=FriendRequestAndDirectMessageSmokeTest test` (5 tests)
- `JAVA_HOME=... mvn verify`
- `npm run lint` and `npm run build` (frontend)
- Local browser smoke test at `http://127.0.0.1:5173/` (agent + owner, 2026-06-17):
  - Send Request → `PENDING`
  - Accept → `ACCEPTED`
  - Send DM → inbox shows `Want to study together?`
  - Refresh (as Learner) → message retained
  - Send request disabled while friends
  - Remove friend → `NONE`, inbox cleared, send re-enabled
  - Check Non-Friend → `403`
  - Block Owner → success
  - Check Blocked DM → `403`

## Follow-Up Notes

- Real caller identity remains deferred to issue #30.
- Full Discord-like Friends Hub and live DM delivery: issue #31.
- DM voice calls: issue #32 (see `docs/architecture/social-hub-and-dm-voice.md`).
