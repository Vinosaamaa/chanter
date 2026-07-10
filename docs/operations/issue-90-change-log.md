# Issue #90 — change log

## Summary

Friend requests inbox at `/app/friends/requests` with accept, decline, cancel, and block actions; nav badge for pending incoming requests; Friends Hub sidebar and Online/All friend filters.

## Acceptance criteria

- [x] `/app/friends/requests` inbox: accept, decline, cancel outgoing, block.
- [x] Nav badge on **Friends** for pending incoming count.
- [x] Friends Hub sidebar: **Friends** + **Pending Requests** entry with badge.
- [x] Friends Hub **Online** / **All** tabs on DM friend list.
- [x] Live DM unchanged (`use-friends-hub` + realtime client).
- [x] No `/dev/demo` required for friend-request flows.
- [x] Backend smoke tests for list + cancel; frontend API + inbox component tests.

## API (message-service)

- `GET /api/v1/friend-requests` — `{ incoming, outgoing }` pending lists (blocked peers excluded).
- `POST /api/v1/friend-requests/{id}/cancellation` — sender withdraws pending request (204).
- `FriendRequestResponse` now includes `createdAt`.

## Tests

```bash
cd backend && mvn -pl message-service test -Dtest=FriendRequestAndDirectMessageSmokeTest
cd frontend && npm run test -- --run friends-api FriendRequestsPage
cd frontend && npm run lint && npm run build
```

## Agent browser testing (2026-07-10)

**Stack:** `make product-supervise` in a long-lived background shell (not one-shot `make product-up`).  
**Teardown (required):** `make product-down` when browser testing finishes.

**Show browser to owner:** `open_resource` with the URL, then `browser_navigate` with `position: "active"` on each step (see `agent-workflow.md`). Do not use silent background navigation.

### Results (pass 2 — clean DB, `{}` POST body fix)

| Step | Result | Notes |
|------|--------|-------|
| Sign in as demo learner | **PASS** | `browser_type` slowly on email/password |
| Top nav **Friends** badge | **PASS** | Shows incoming count |
| Sidebar **Pending Requests** badge | **PASS** | |
| Incoming row + Accept/Decline/Block | **PASS** | |
| **Accept** via UI click | **PASS** | Inbox clears; badges removed (pass 1 failed — see below) |
| **Decline** via UI click | **Not retried** | Pass 1 used API after stale element refs |
| **Cancel** outgoing (owner UI) | **Not retried** | API verified |
| **Block** + resend 403 | **API PASS** | UI not re-run after block |
| Friends Hub **Online** / **All** | **PASS** | |
| DM send | **PASS** | |

### Pass 1 issues (resolved or explained)

1. **“You do not have permission” (403)** on Accept — leftover `user_blocks` rows from block testing blocked new friend requests; stale inbox could show actions that then failed. **Fix:** clear blocks before inbox tests; accept retest **PASS**.
2. **Accept click looked stuck** — concurrent API accept during UI test + missing `void` on async handler. **Fix:** `void runAction(...)` and POST `body: '{}'` on acceptance/decline/cancel.
3. **Browser not visible to owner** — agent used background navigation. **Fix:** `agent-workflow.md` — `open_resource` + `position: "active"` reveal sequence.

### Seed data (no production send UI yet)

```bash
make product-health
# Owner → learner pending request (demo users from product-demo-seed)
DEMO_PASSWORD=chanter-dev-demo ./scripts/seed-workable-product-demo.sh  # if needed
# Or API: remove friendship, POST /api/v1/friend-requests
```

## Browser check (manual — owner)

1. `make product-supervise` (or `make product-up` in your terminal).
2. Sign in as two users who share a Study Server (two browser profiles).
3. Seed a pending request (API or `/dev/demo` send flow).
4. Learner: `/app/friends/requests` — accept, decline, block.
5. Owner: **Outgoing** tab — cancel.
6. Confirm badges, Friends Hub tabs, and live DM after accept.
7. `make product-down` when finished.

## Product stack scripts (agent)

- `make product-supervise` — sticky stack for agent browser sessions.
- `make product-down` — **always** after agent browser testing.

## Non-goals (deferred)

- Display names / mutual-friends metadata — stretch; still uses `Friend {id-prefix}` labels.
- “Add Friends” search tab from mockup — out of scope for #90; **send UI → [#109](https://github.com/Vinosaamaa/chanter/issues/109)** (co-member picker, not global search).
